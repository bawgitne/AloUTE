package com.aloute.integration;

import com.aloute.common.exception.ApiException;
import com.aloute.entity.Poll;
import com.aloute.entity.PollOption;
import com.aloute.entity.PollVote;
import com.aloute.entity.Post;
import com.aloute.entity.PostVisibility;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.PollOptionRepository;
import com.aloute.repository.PollRepository;
import com.aloute.repository.PollVoteRepository;
import com.aloute.repository.PostRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.PollService;
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
    @Autowired private PostRepository postRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PollService pollService;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

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
    @DisplayName("a vote payload with more than 10 option IDs is refused before any lookup")
    void oversizedVotePayloadIsRefused() throws Exception {
        long postId = createPostWithPoll(pollJson("Q?", List.of("a", "b"), true, null));
        long pollId = pollIdOf(postId);

        // 11 synthetic ids exceed the VotePollRequest cap (a poll holds at most 10 options),
        // so the request is rejected by validation without touching option lookup.
        StringBuilder ids = new StringBuilder();
        for (long i = 1; i <= 11; i++) {
            if (i > 1) ids.append(',');
            ids.append(i);
        }
        mockMvc.perform(post("/api/polls/{pollId}/vote", pollId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionIds\":[" + ids + "]}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(voterAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        assertThat(pollVoteRepository.countByPollId(pollId)).isZero();
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

    // -------------------------------------------------------------------------
    // Genuine concurrency: separate transactions, separate threads, latch start
    // -------------------------------------------------------------------------

    /** Poll + two users seeded in a committed transaction so racing transactions can see them. */
    private record ConcurrencyFixture(long pollId, long optionA, long optionB, User racer1, User racer2, long postId) {}

    /** REQUIRES_NEW template: the @Transactional test transaction must not join these. */
    private org.springframework.transaction.support.TransactionTemplate newTxTemplate() {
        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    private ConcurrencyFixture seedConcurrencyFixture(boolean allowMultiple) {
        return newTxTemplate().execute(status -> {
            long suffix = System.nanoTime();
            User r1 = newUser("race1_" + suffix, "Racer One");
            User r2 = newUser("race2_" + suffix, "Racer Two");

            Post post = new Post();
            post.setUser(r1);
            post.setContent("race post " + suffix);
            post.setVisibility(PostVisibility.PUBLIC);
            post.setAllowReply(true);
            post.setAllowQuote(true);
            post = postRepository.save(post);

            Poll poll = pollRepository.save(Poll.builder()
                    .post(post)
                    .question("Race poll " + suffix)
                    .allowMultiple(allowMultiple)
                    .build());
            PollOption a = pollOptionRepository.save(PollOption.builder().poll(poll).optionText("A").position(0).build());
            PollOption b = pollOptionRepository.save(PollOption.builder().poll(poll).optionText("B").position(1).build());
            return new ConcurrencyFixture(poll.getId(), a.getId(), b.getId(), r1, r2, post.getId());
        });
    }

    private void cleanupConcurrencyFixture(ConcurrencyFixture f) {
        newTxTemplate().executeWithoutResult(status ->
                pollVoteRepository.deleteAll(pollVoteRepository.findByPollId(f.pollId())));
    }

    /** Runs one vote in its own committed transaction, started simultaneously with the other racer. */
    private java.util.concurrent.Future<String> startRacer(java.util.concurrent.ExecutorService pool,
                                                            java.util.concurrent.CountDownLatch startGate,
                                                            long pollId, User racer, long optionId) {
        return pool.submit(() -> {
            try {
                startGate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "interrupted";
            }
            org.springframework.transaction.support.TransactionTemplate tx = newTxTemplate();
            try {
                tx.executeWithoutResult(status ->
                        pollService.votePoll(pollId, racer.getId(),
                                com.aloute.dto.request.VotePollRequest.builder().optionId(optionId).build()));
                return "OK";
            } catch (Exception e) {
                return e instanceof com.aloute.common.exception.ApiException api && "POLL_ALREADY_VOTED".equals(api.getCode())
                        ? "POLL_ALREADY_VOTED"
                        : "UNEXPECTED:" + e.getClass().getSimpleName();
            }
        });
    }

    @Test
    @DisplayName("concurrent same-user votes for DIFFERENT options: exactly one wins, one-vote invariant holds")
    void concurrentSameUserDifferentOptionsSingleChoice() throws Exception {
        ConcurrencyFixture f = seedConcurrencyFixture(false);
        try {
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            java.util.concurrent.CountDownLatch startGate = new java.util.concurrent.CountDownLatch(1);
            try {
                var r1 = startRacer(pool, startGate, f.pollId(), f.racer1(), f.optionA());
                var r2 = startRacer(pool, startGate, f.pollId(), f.racer1(), f.optionB()); // SAME user, different option
                startGate.countDown();
                String result1 = r1.get(30, java.util.concurrent.TimeUnit.SECONDS);
                String result2 = r2.get(30, java.util.concurrent.TimeUnit.SECONDS);

                // Exactly one transaction may win; the loser must see the committed vote
                // through the poll row lock and fail with POLL_ALREADY_VOTED.
                assertThat(result1.startsWith("UNEXPECTED")).as("racer1 result: %s", result1).isFalse();
                assertThat(result2.startsWith("UNEXPECTED")).as("racer2 result: %s", result2).isFalse();
                long successes = java.util.stream.Stream.of(result1, result2).filter("OK"::equals).count();
                long rejections = java.util.stream.Stream.of(result1, result2).filter("POLL_ALREADY_VOTED"::equals).count();
                assertThat(successes).isEqualTo(1);
                assertThat(rejections).isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }

            // Exactly one vote row, exactly one incremented option, totals consistent.
            org.springframework.transaction.support.TransactionTemplate readTx = newTxTemplate();
            readTx.executeWithoutResult(status -> {
                assertThat(pollVoteRepository.countByPollId(f.pollId())).isEqualTo(1);
                List<PollOption> options = pollOptionRepository.findByPollIdOrderByPositionAsc(f.pollId());
                int incremented = options.stream().mapToInt(PollOption::getVoteCount).filter(c -> c == 1).sum();
                assertThat(incremented).as("exactly one option has voteCount 1").isEqualTo(1);
                Poll poll = pollRepository.findById(f.pollId()).orElseThrow();
                assertThat(pollService.mapToPollResponse(poll, f.racer1().getId()).getTotalVotes()).isEqualTo(1);
            });
        } finally {
            cleanupConcurrencyFixture(f);
        }
    }

    @Test
    @DisplayName("concurrent different-user votes: both succeed, atomic counts, no interference")
    void concurrentDifferentUsersBothSucceed() throws Exception {
        ConcurrencyFixture f = seedConcurrencyFixture(false);
        try {
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            java.util.concurrent.CountDownLatch startGate = new java.util.concurrent.CountDownLatch(1);
            try {
                var r1 = startRacer(pool, startGate, f.pollId(), f.racer1(), f.optionA());
                var r2 = startRacer(pool, startGate, f.pollId(), f.racer2(), f.optionB()); // different users
                startGate.countDown();
                String result1 = r1.get(30, java.util.concurrent.TimeUnit.SECONDS);
                String result2 = r2.get(30, java.util.concurrent.TimeUnit.SECONDS);

                assertThat(result1).isEqualTo("OK");
                assertThat(result2).isEqualTo("OK");
            } finally {
                pool.shutdownNow();
            }

            org.springframework.transaction.support.TransactionTemplate readTx = newTxTemplate();
            readTx.executeWithoutResult(status -> {
                assertThat(pollVoteRepository.countByPollId(f.pollId())).isEqualTo(2);
                List<PollOption> options = pollOptionRepository.findByPollIdOrderByPositionAsc(f.pollId());
                assertThat(options).extracting(PollOption::getVoteCount).containsExactly(1, 1);
                Poll poll = pollRepository.findById(f.pollId()).orElseThrow();
                assertThat(pollService.mapToPollResponse(poll, null).getTotalVotes()).isEqualTo(2);
            });
        } finally {
            cleanupConcurrencyFixture(f);
        }
    }
}
