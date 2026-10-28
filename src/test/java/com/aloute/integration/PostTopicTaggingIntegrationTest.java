package com.aloute.integration;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.request.CreatePostRequest;
import com.aloute.dto.response.PostResponse;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.security.authorization.Role;
import com.aloute.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Topic tagging round-trip integration tests against a real H2 relational DB.
 * Covers the P0 "gắn Topic vào bài viết" flow end to end at the service layer:
 * - 0 topics, 1 valid topic, multiple valid topics
 * - duplicate topic ids collapse to a single PostTopic link
 * - a nonexistent topic id is ignored (never fails the post)
 * - response payload exposes the attached topics (save → read roundtrip)
 * - topic feed respects post visibility (FOLLOWERS-only posts stay hidden)
 * - topic feed respects private-community membership
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class PostTopicTaggingIntegrationTest {

    @Autowired private PostService postService;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private CommunityMemberRepository communityMemberRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private PostTopicRepository postTopicRepository;

    private User author;
    private User viewer;
    private User outsider;

    private User createUser(String username, String email) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash("$2a$10$hashedPasswordPlaceholder");
        u.setVerified(true);
        u.setPrivateAccount(false);
        u.setActive(true);
        u.setRole(Role.USER);
        u = userRepository.save(u);
        Profile p = new Profile();
        p.setUser(u);
        p.setDisplayName("Display " + username);
        profileRepository.save(p);
        return u;
    }

    private Topic createTopic(String name) {
        Topic t = new Topic();
        t.setName(name);
        t.setSlug(name.toLowerCase().replaceAll("[^a-z0-9]+", "-"));
        return topicRepository.save(t);
    }

    private CreatePostRequest request(String content, List<Long> topicIds) {
        return new CreatePostRequest(content, PostVisibility.PUBLIC, true, true, null, null, topicIds, null);
    }

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        author = createUser("topic_author_" + suffix, "topic_author_" + suffix + "@aloute.test");
        viewer = createUser("topic_viewer_" + suffix, "topic_viewer_" + suffix + "@aloute.test");
        outsider = createUser("topic_outsider_" + suffix, "topic_outsider_" + suffix + "@aloute.test");
    }

    @Test
    @DisplayName("Post with zero topics saves no PostTopic rows")
    void zeroTopicsSavesNoLinks() {
        PostResponse created = postService.create(author.getId(), request("Không gắn chủ đề", null), null, null, null);
        assertThat(created).isNotNull();
        assertThat(created.topics()).isNullOrEmpty();
        assertThat(postTopicRepository.findByPost_IdIn(List.of(created.id()))).isEmpty();
    }

    @Test
    @DisplayName("Post with one valid topic persists a PostTopic link and returns it on read")
    void oneValidTopicRoundTrips() {
        Topic topic = createTopic("Học tập " + System.nanoTime());
        PostResponse created = postService.create(author.getId(), request("Một chủ đề", List.of(topic.getId())), null, null, null);

        assertThat(created.topics()).extracting("id").containsExactly(topic.getId());

        // Save → read roundtrip via the feed assembly.
        ApiResponse<List<PostResponse>> feed = postService.listPublic(0, 50, author.getId());
        PostResponse reloaded = feed.data().stream().filter(p -> p.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(reloaded.topics()).extracting("id").containsExactly(topic.getId());
        assertThat(reloaded.topics()).extracting("name").containsExactly(topic.getName());
    }

    @Test
    @DisplayName("Post with multiple valid topics persists every link")
    void multipleValidTopics() {
        Topic t1 = createTopic("Alpha " + System.nanoTime());
        Topic t2 = createTopic("Beta " + System.nanoTime());
        PostResponse created = postService.create(author.getId(), request("Nhiều chủ đề", List.of(t1.getId(), t2.getId())), null, null, null);

        assertThat(created.topics()).extracting("id").containsExactlyInAnyOrder(t1.getId(), t2.getId());
    }

    @Test
    @DisplayName("Duplicate topic ids collapse to a single link")
    void duplicateTopicIdsCollapse() {
        Topic topic = createTopic("Trùng " + System.nanoTime());
        PostResponse created = postService.create(
                author.getId(), request("Trùng chủ đề", List.of(topic.getId(), topic.getId())), null, null, null);

        assertThat(postTopicRepository.findByPost_IdIn(List.of(created.id()))).hasSize(1);
    }

    @Test
    @DisplayName("A nonexistent topic id is ignored without failing the post")
    void nonexistentTopicIdIgnored() {
        Topic valid = createTopic("Hợp lệ " + System.nanoTime());
        PostResponse created = postService.create(
                author.getId(), request("Id không tồn tại", List.of(valid.getId(), 9_999_999L)), null, null, null);

        assertThat(created).isNotNull();
        assertThat(created.topics()).extracting("id").containsExactly(valid.getId());
    }

    @Test
    @DisplayName("Topic feed lists public posts and hides FOLLOWERS-only posts from non-followers")
    void topicFeedRespectsPostVisibility() {
        Topic topic = createTopic("Riêng tư " + System.nanoTime());
        CreatePostRequest followersOnly = new CreatePostRequest(
                "Followers only", PostVisibility.FOLLOWERS, true, true, null, null, List.of(topic.getId()), null);
        PostResponse created = postService.create(author.getId(), followersOnly, null, null, null);

        // Author always sees their own post.
        ApiResponse<List<PostResponse>> asAuthor = postService.topicPosts(topic.getId(), 0, 50, author.getId());
        assertThat(asAuthor.data()).extracting(PostResponse::id).contains(created.id());

        // A non-follower must not see the FOLLOWERS-only post in the topic feed.
        ApiResponse<List<PostResponse>> asViewer = postService.topicPosts(topic.getId(), 0, 50, viewer.getId());
        assertThat(asViewer.data()).extracting(PostResponse::id).doesNotContain(created.id());

        // Guest must not see it either.
        ApiResponse<List<PostResponse>> asGuest = postService.topicPosts(topic.getId(), 0, 50, null);
        assertThat(asGuest.data()).extracting(PostResponse::id).doesNotContain(created.id());
    }

    @Test
    @DisplayName("Topic feed does not leak private-community posts to non-members")
    void topicFeedRespectsPrivateCommunity() {
        Topic topic = createTopic("Cộng đồng kín " + System.nanoTime());
        Community community = new Community();
        community.setOwner(author);
        community.setName("Private " + System.nanoTime());
        community.setSlug("private-" + System.nanoTime());
        community.setPrivateCommunity(true);
        community.setMemberCount(1);
        community.setPostCount(0);
        community = communityRepository.save(community);
        communityMemberRepository.save(new CommunityMember(community, author, CommunityRole.ADMIN));

        PostResponse created = postService.create(
                author.getId(), request("Bài trong cộng đồng kín", List.of(topic.getId())), community.getId(), null, null);

        // Author (member) sees it.
        assertThat(postService.topicPosts(topic.getId(), 0, 50, author.getId()).data())
                .extracting(PostResponse::id).contains(created.id());
        // Outsider does not.
        assertThat(postService.topicPosts(topic.getId(), 0, 50, outsider.getId()).data())
                .extracting(PostResponse::id).doesNotContain(created.id());
    }
}
