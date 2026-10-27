package com.aloute.integration;

import com.aloute.entity.Community;
import com.aloute.entity.CommunityMember;
import com.aloute.entity.CommunityRole;
import com.aloute.entity.Follow;
import com.aloute.entity.FollowId;
import com.aloute.entity.Poll;
import com.aloute.entity.PollOption;
import com.aloute.entity.Post;
import com.aloute.entity.PostModerationStatus;
import com.aloute.entity.PostVisibility;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.CommunityMemberRepository;
import com.aloute.repository.CommunityRepository;
import com.aloute.repository.FollowRepository;
import com.aloute.repository.PollRepository;
import com.aloute.repository.PostRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.principal.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-04: a Poll is readable/votable only through the visibility of its parent Post
 * (domain §14/§17). Every denied read/vote must conceal existence with 404, matching
 * the canonical private-community concealment policy (PR #29/#30).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class PollVisibilityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PollRepository pollRepository;
    @Autowired private FollowRepository followRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private CommunityMemberRepository communityMemberRepository;

    private Authentication authorAuth;
    private Authentication followerAuth;
    private Authentication outsiderAuth;
    private Authentication commOwnerAuth;
    private User author;
    private User follower;
    private User outsider;
    private User commOwner;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        author = newUser("pollvis_author_" + suffix, "Poll Vis Author");
        follower = newUser("pollvis_follower_" + suffix, "Poll Vis Follower");
        outsider = newUser("pollvis_outsider_" + suffix, "Poll Vis Outsider");
        commOwner = newUser("pollvis_cowner_" + suffix, "Poll Vis Community Owner");
        authorAuth = authOf(author);
        followerAuth = authOf(follower);
        outsiderAuth = authOf(outsider);
        commOwnerAuth = authOf(commOwner);
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

    private Post createPost(User postAuthor, PostVisibility visibility, Community community,
                            PostModerationStatus moderationStatus, Instant deletedAt) {
        Post p = new Post();
        p.setUser(postAuthor);
        p.setContent("Poll visibility fixture post");
        p.setVisibility(visibility);
        p.setCommunity(community);
        p.setAllowReply(true);
        p.setAllowQuote(true);
        p.setModerationStatus(moderationStatus);
        p.setDeletedAt(deletedAt);
        return postRepository.save(p);
    }

    /** Attaches a two-option single-choice poll through the repository and returns the poll id. */
    private long attachPoll(Post post) {
        Poll poll = Poll.builder()
                .post(post)
                .question("Visibility check poll?")
                .allowMultiple(false)
                .build();
        PollOption first = new PollOption();
        first.setPoll(poll);
        first.setOptionText("Option A");
        first.setPosition(0);
        PollOption second = new PollOption();
        second.setPoll(poll);
        second.setOptionText("Option B");
        second.setPosition(1);
        poll.getOptions().add(first);
        poll.getOptions().add(second);
        return pollRepository.save(poll).getId();
    }

    private long firstOptionId(long pollId) {
        return pollRepository.findById(pollId).orElseThrow().getOptions().get(0).getId();
    }

    private void follow(User a, User b) {
        Follow f = new Follow();
        f.setId(new FollowId(a.getId(), b.getId()));
        f.setFollower(a);
        f.setFollowing(b);
        followRepository.save(f);
    }

    private Community createCommunity(String name, String slug, User owner, boolean isPrivate) {
        Community c = new Community();
        c.setName(name);
        c.setSlug(slug);
        c.setOwner(owner);
        c.setPrivateCommunity(isPrivate);
        return communityRepository.save(c);
    }

    private void join(Community community, User user, CommunityRole role) {
        CommunityMember member = new CommunityMember();
        member.setCommunity(community);
        member.setUser(user);
        member.setRole(role);
        communityMemberRepository.save(member);
    }

    private String voteBody(long optionId) {
        return "{\"optionIds\":[" + optionId + "]}";
    }

    @Test
    @DisplayName("Regression: poll on PUBLIC post remains readable by guest")
    void publicPostPollRemainsReadableByGuest() throws Exception {
        Post post = createPost(author, PostVisibility.PUBLIC, null, null, null);
        long pollId = attachPoll(post);

        mockMvc.perform(get("/api/polls/" + pollId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(pollId));
    }

    @Test
    @DisplayName("BL-04: poll on FOLLOWERS post is 404 for guest and non-follower, 200 for follower")
    void followersPostPollConcealedFromGuestAndNonFollower() throws Exception {
        follow(follower, author);
        Post post = createPost(author, PostVisibility.FOLLOWERS, null, null, null);
        long pollId = attachPoll(post);

        mockMvc.perform(get("/api/polls/" + pollId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/polls/" + pollId).with(authentication(outsiderAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/polls/" + pollId).with(authentication(followerAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("BL-04: vote on FOLLOWERS post poll is 404 for non-follower, 200 for follower")
    void followersPostPollVoteRequiresFollowerRelation() throws Exception {
        follow(follower, author);
        Post post = createPost(author, PostVisibility.FOLLOWERS, null, null, null);
        long pollId = attachPoll(post);
        long optionId = firstOptionId(pollId);

        mockMvc.perform(post("/api/polls/" + pollId + "/votes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voteBody(optionId))
                        .with(authentication(outsiderAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/polls/" + pollId + "/votes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voteBody(optionId))
                        .with(authentication(followerAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.options[0].selectedByCurrentUser").value(true));
    }

    @Test
    @DisplayName("Guest vote is rejected with 401 on any poll")
    void guestCannotVoteOnAnyPoll() throws Exception {
        Post post = createPost(author, PostVisibility.PUBLIC, null, null, null);
        long pollId = attachPoll(post);
        long optionId = firstOptionId(pollId);

        mockMvc.perform(post("/api/polls/" + pollId + "/votes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voteBody(optionId))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("BL-04: poll on private community post is 404 for outsider, 200 for member/owner")
    void privateCommunityPostPollConcealedFromOutsider() throws Exception {
        Community community = createCommunity("Poll Private Comm", "pollvis-private-" + System.nanoTime(),
                commOwner, true);
        join(community, author, CommunityRole.MEMBER);
        Post post = createPost(author, PostVisibility.PUBLIC, community, PostModerationStatus.APPROVED, null);
        long pollId = attachPoll(post);
        long optionId = firstOptionId(pollId);

        mockMvc.perform(get("/api/polls/" + pollId).with(authentication(outsiderAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/polls/" + pollId + "/votes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voteBody(optionId))
                        .with(authentication(outsiderAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/polls/" + pollId).with(authentication(followerAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/polls/" + pollId).with(authentication(commOwnerAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("BL-04: poll on PENDING community submission is 404 for ordinary member, 200 for author/owner")
    void pendingCommunitySubmissionPollHiddenFromOrdinaryMembers() throws Exception {
        Community community = createCommunity("Poll Pending Comm", "pollvis-pending-" + System.nanoTime(),
                commOwner, false);
        join(community, author, CommunityRole.MEMBER);
        join(community, follower, CommunityRole.MEMBER);
        Post post = createPost(author, PostVisibility.PUBLIC, community, PostModerationStatus.PENDING, null);
        long pollId = attachPoll(post);
        long optionId = firstOptionId(pollId);

        mockMvc.perform(get("/api/polls/" + pollId).with(authentication(followerAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/polls/" + pollId + "/votes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voteBody(optionId))
                        .with(authentication(followerAuth))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/polls/" + pollId).with(authentication(authorAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/polls/" + pollId).with(authentication(commOwnerAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("BL-04: poll on REJECTED community submission is 404 for ordinary member and outsider")
    void rejectedCommunitySubmissionPollHidden() throws Exception {
        Community community = createCommunity("Poll Rejected Comm", "pollvis-rejected-" + System.nanoTime(),
                commOwner, false);
        join(community, author, CommunityRole.MEMBER);
        join(community, follower, CommunityRole.MEMBER);
        Post post = createPost(author, PostVisibility.PUBLIC, community, PostModerationStatus.REJECTED, null);
        long pollId = attachPoll(post);

        mockMvc.perform(get("/api/polls/" + pollId).with(authentication(followerAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/polls/" + pollId).with(authentication(outsiderAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/polls/" + pollId).with(authentication(authorAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("BL-04: poll on deleted post is 404 even for its author")
    void deletedPostPollConcealedEvenForAuthor() throws Exception {
        Post post = createPost(author, PostVisibility.PUBLIC, null, null, Instant.now());
        long pollId = attachPoll(post);

        mockMvc.perform(get("/api/polls/" + pollId).with(authentication(authorAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
