package com.aloute.integration;

import com.aloute.entity.Poll;
import com.aloute.entity.PollOption;
import com.aloute.entity.PollVote;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.PollOptionRepository;
import com.aloute.repository.PollRepository;
import com.aloute.repository.PollVoteRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.principal.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end poll lifecycle: create (through the normal post-creation flow) → vote →
 * refresh → results, plus the validation, single-choice one-vote, closed-poll and
 * concurrency-safety rules from domain §17.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class PollEndToEndIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private PollRepository pollRepository;
    @Autowired private PollOptionRepository pollOptionRepository;
    @Autowired private PollVoteRepository pollVoteRepository;
    @Autowired private ObjectMapper objectMapper;

    private Authentication authorAuth;
    private Authentication voterAuth;
    private User author;
    private User voter;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        author = newUser("pollauthor_" + suffix, "Poll Author");
        authorAuth = authOf(author);
        voter = newUser("pollvoter_" + suffix, "Poll Voter");
        voterAuth = authOf(voter);
    }

    private User newUser(String username, String displayName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@aloute.test");
        user.setPasswordHash("$2a$10$placeholder");
        user.setVerified(true);
        user.setActive(true);
        user = userRepository.save(user);
        Profile profile = new Profile();
        profile.setUser(user);
        profile.setDisplayName(displayName);
        profileRepository.save(profile);
        return user;
    }

    private Authentication authOf(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private long createPostWithPoll(String pollJson) throws Exception {
        String body = "{\"content\":\"Bai viet kem binh chon\",\"visibility\":\"PUBLIC\""
                + (pollJson != null ? ",\"poll\":" + pollJson : "") + "}";
        MvcResult result = mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authorAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    private long[] optionIdsOf(long postId, Authentication as) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/posts/{id}", postId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(as)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode options = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("poll").path("options");
        long[] ids = new long[options.size()];
        for (int i = 0; i < options.size(); i++) {
            ids[i] = options.get(i).path("id").asLong();
        }
        return ids;
    }

    private String pollJson(String question, List<String> options, boolean allowMultiple, Integer durationDays) {
        StringBuilder sb = new StringBuilder("{\"question\":\"").append(question).append("\",\"options\":[");
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(options.get(i)).append('"');
        }
        sb.append("],\"allowMultiple\":").append(allowMultiple);
        if (durationDays != null) {
            sb.append(",\"durationDays\":").append(durationDays);
        }
        return sb.append('}').toString();
    }

    // -------------------------------------------------------------------------
    // Create → refresh → vote → results
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("create → refresh: post creation flow persists poll with ordered options")
    void createPersistsPollThroughPostCreationFlow() throws Exception {
        long postId = createPostWithPoll(
                pollJson("Chu de nao ban thich nhat?", List.of("CSDL", "Web API", "Bao mat"), false, null));

        mockMvc.perform(get("/api/posts/{id}", postId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authorAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.poll").exists())
                .andExpect(jsonPath("$.data.poll.question").value("Chu de nao ban thich nhat?"))
                .andExpect(jsonPath("$.data.poll.allowMultiple").value(false))
                .andExpect(jsonPath("$.data.poll.totalVotes").value(0))
                .andExpect(jsonPath("$.data.poll.options.length()").value(3))
                .andExpect(jsonPath("$.data.poll.options[0].text").value("CSDL"))
                .andExpect(jsonPath("$.data.poll.options[1].text").value("Web API"))
                .andExpect(jsonPath("$.data.poll.options[2].text").value("Bao mat"))
                .andExpect(jsonPath("$.data.poll.options[0].voteCount").value(0));

        // Rows persisted with positions
        Poll poll = pollRepository.findByPostId(postId).orElseThrow();
        List<PollOption> stored = pollOptionRepository.findByPollIdOrderByPositionAsc(poll.getId());
        assertThat(stored).extracting(PollOption::getPosition).containsExactly(0, 1, 2);
        assertThat(stored).extracting(PollOption::getVoteCount).containsExactly(0, 0, 0);
    }

    @Test
    @DisplayName("create → vote → refresh → results: counts, own-selection and one-vote rule survive a re-read")
    void createVoteRefreshResults() throws Exception {
        long postId = createPostWithPoll(
                pollJson("Chon mon hoc", List.of("CNPM", "KTLT"), false, null));
        long[] optionIds = optionIdsOf(postId, authorAuth);

        // Another user votes option 1
        mockMvc.perform(post("/api/polls/{pollId}/vote", pollIdOf(postId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":" + optionIds[0] + "}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(voterAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalVotes").value(1))
                .andExpect(jsonPath("$.data.userVotedOptionId").value(optionIds[0]))
                .andExpect(jsonPath("$.data.options[0].voteCount").value(1))
                .andExpect(jsonPath("$.data.options[0].selectedByCurrentUser").value(true));

        // Refresh the post: results state is stable, author still sees the vote form
        mockMvc.perform(get("/api/posts/{id}", postId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(voterAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.poll.totalVotes").value(1))
                .andExpect(jsonPath("$.data.poll.userVotedOptionId").value(optionIds[0]));
        mockMvc.perform(get("/api/posts/{id}", postId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authorAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.poll.totalVotes").value(1))
                .andExpect(jsonPath("$.data.poll.userVotedOptionId").doesNotExist());

        // Same user voting again is rejected
        mockMvc.perform(post("/api/polls/{pollId}/vote", pollIdOf(postId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":" + optionIds[1] + "}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(voterAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("POLL_ALREADY_VOTED"));

        // Counts unchanged after the rejected vote
        mockMvc.perform(get("/api/posts/{id}", postId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authorAuth)))
                .andExpect(jsonPath("$.data.poll.totalVotes").value(1));
    }

    private long pollIdOf(long postId) {
        return pollRepository.findByPostId(postId).orElseThrow().getId();
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("poll validation: 1 option, 11 options, blank option, blank question and bad duration are rejected with 422")
    void pollInputValidation() throws Exception {
        expectPollCreationFailure(pollJson("Q?", List.of("only-one"), false, null));
        expectPollCreationFailure(pollJson("Q?", List.of(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"), false, null));
        expectPollCreationFailure("{\"question\":\"Q?\",\"options\":[\"a\",\"  \"],\"allowMultiple\":false}");
        expectPollCreationFailure("{\"question\":\"   \",\"options\":[\"a\",\"b\"],\"allowMultiple\":false}");
        expectPollCreationFailure(pollJson("Q?", List.of("a", "b"), false, 0));
        expectPollCreationFailure(pollJson("Q?", List.of("a", "b"), false, 400));
    }

    private void expectPollCreationFailure(String pollJson) throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"noi dung\",\"visibility\":\"PUBLIC\",\"poll\":" + pollJson + "}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authorAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("durationDays sets a future closesAt; without it the poll stays open")
    void durationDaysProducesClosesAt() throws Exception {
        long postId = createPostWithPoll(pollJson("Q?", List.of("a", "b"), false, 3));
        Poll poll = pollRepository.findByPostId(postId).orElseThrow();
        assertThat(poll.getClosedAt()).isAfter(Instant.now().plusSeconds(2 * 86400 - 3600));
        assertThat(poll.isClosed()).isFalse();

        long postId2 = createPostWithPoll(pollJson("Q2?", List.of("a", "b"), false, null));
        assertThat(pollRepository.findByPostId(postId2).orElseThrow().getClosedAt()).isNull();
    }

    // -------------------------------------------------------------------------
    // Voting rules
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("closed poll rejects votes with POLL_CLOSED and the post card sees the closed state")
    void closedPollRejectsVotes() throws Exception {
        long postId = createPostWithPoll(pollJson("Q?", List.of("a", "b"), false, null));
        Poll poll = pollRepository.findByPostId(postId).orElseThrow();
        poll.setClosedAt(Instant.now().minusSeconds(60));
        pollRepository.saveAndFlush(poll);

        long[] optionIds = optionIdsOf(postId, authorAuth);
        mockMvc.perform(post("/api/polls/{pollId}/vote", poll.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":" + optionIds[0] + "}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(voterAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("POLL_CLOSED"));

        assertThat(pollVoteRepository.countByPollId(poll.getId())).isZero();
    }

    @Test
    @DisplayName("single-choice rejects multi-option payloads and options from another poll")
    void singleChoiceAndForeignOptionRules() throws Exception {
        long postId = createPostWithPoll(pollJson("Q?", List.of("a", "b"), false, null));
        long postIdOther = createPostWithPoll(pollJson("Q2?", List.of("x", "y"), false, null));
        long[] optionIds = optionIdsOf(postId, authorAuth);
        long foreignOptionId = optionIdsOf(postIdOther, authorAuth)[0];
        long pollId = pollIdOf(postId);

        // Single choice: two options in one request → 400
        mockMvc.perform(post("/api/polls/{pollId}/vote", pollId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionIds\":[" + optionIds[0] + "," + optionIds[1] + "]}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(voterAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("POLL_NOT_MULTIPLE_CHOICE"));

        // Foreign option → 400, nothing recorded
        mockMvc.perform(post("/api/polls/{pollId}/vote", pollId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":" + foreignOptionId + "}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(voterAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());
        assertThat(pollVoteRepository.countByPollId(pollId)).isZero();

        // Nonexistent option → 404
        mockMvc.perform(post("/api/polls/{pollId}/vote", pollId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":999999999}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(voterAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POLL_OPTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("multi-choice poll: several options in one vote, later votes only add new options")
    void multiChoiceVoting() throws Exception {
        long postId = createPostWithPoll(pollJson("Q?", List.of("a", "b", "c"), true, null));
        long[] optionIds = optionIdsOf(postId, authorAuth);
        long pollId = pollIdOf(postId);

        mockMvc.perform(post("/api/polls/{pollId}/vote", pollId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionIds\":[" + optionIds[0] + "," + optionIds[1] + "]}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(voterAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalVotes").value(2))
                .andExpect(jsonPath("$.data.options[0].voteCount").value(1))
                .andExpect(jsonPath("$.data.options[1].voteCount").value(1))
                .andExpect(jsonPath("$.data.options[2].voteCount").value(0));

        // Duplicate payload (same options + one new) → no double counting
        mockMvc.perform(post("/api/polls/{pollId}/vote", pollId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionIds\":[" + optionIds[0] + "," + optionIds[1] + "," + optionIds[2] + "]}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(voterAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalVotes").value(3))
                .andExpect(jsonPath("$.data.options[0].voteCount").value(1))
                .andExpect(jsonPath("$.data.options[2].voteCount").value(1));

        assertThat(pollVoteRepository.countByPollId(pollId)).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Concurrency safety
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("database unique constraint is the final one-vote arbiter under concurrent inserts")
    void uniqueConstraintBlocksDuplicateVoteRows() throws Exception {
        long postId = createPostWithPoll(pollJson("Q?", List.of("a", "b"), false, null));
        Poll poll = pollRepository.findByPostId(postId).orElseThrow();
        PollOption option = pollOptionRepository.findByPollIdOrderByPositionAsc(poll.getId()).get(0);

        PollVote first = PollVote.builder().poll(poll).option(option).user(voter).build();
        pollVoteRepository.saveAndFlush(first);

        // A racing transaction that skipped the exists() check still cannot insert twice.
        // Assert the constraint violation itself; the persistence session is unusable
        // after such an exception, so no further queries are issued in this test —
        // the whole transaction rolls back with the test.
        PollVote duplicate = PollVote.builder().poll(poll).option(option).user(voter).build();
        AtomicReference<Exception> thrown = new AtomicReference<>();
        try {
            pollVoteRepository.saveAndFlush(duplicate);
        } catch (Exception e) {
            thrown.set(e);
        }
        assertThat(thrown.get()).isNotNull();
        assertThat(thrown.get()).isInstanceOf(DataIntegrityViolationException.class);
    }
}
