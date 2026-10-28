package com.aloute.integration;

import com.aloute.dto.response.PostResponse;
import com.aloute.entity.MediaAsset;
import com.aloute.entity.MediaPurpose;
import com.aloute.entity.MediaType;
import com.aloute.entity.Poll;
import com.aloute.entity.PollOption;
import com.aloute.entity.PollVote;
import com.aloute.entity.Post;
import com.aloute.entity.PostMedia;
import com.aloute.entity.PostTopic;
import com.aloute.entity.PostVisibility;
import com.aloute.entity.Profile;
import com.aloute.entity.Topic;
import com.aloute.entity.User;
import com.aloute.repository.MediaAssetRepository;
import com.aloute.repository.PollOptionRepository;
import com.aloute.repository.PollRepository;
import com.aloute.repository.PollVoteRepository;
import com.aloute.repository.PostMediaRepository;
import com.aloute.repository.PostRepository;
import com.aloute.repository.PostTopicRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.TopicRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.PollService;
import com.aloute.service.PostService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query-count regression tests for the batched feed assembly
 * (PostServiceImpl#toResponses, perf commit eda0757 and its follow-ups).
 *
 * A realistic page is built in-test: 20 top-level posts from 6 authors, with
 * media on 6 posts, topics on 8 posts, and 3 polls (4 options each) with votes
 * from two users each. The persistence context is cleared before counting so
 * the service call pays the same lazy loads a real request would. Actual
 * statement counts are printed for the evidence log.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class FeedQueryCountIntegrationTest {

    private static final int POST_COUNT = 20;
    private static final int AUTHOR_COUNT = 6;
    private static final int MEDIA_POSTS = 6;
    private static final int TOPIC_POSTS = 8;
    private static final int POLL_POSTS = 3;
    private static final int POLL_OPTIONS = 4;

    @Autowired private PostService postService;
    @Autowired private PollService pollService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private MediaAssetRepository mediaAssetRepository;
    @Autowired private PostMediaRepository postMediaRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private PostTopicRepository postTopicRepository;
    @Autowired private PollRepository pollRepository;
    @Autowired private PollOptionRepository pollOptionRepository;
    @Autowired private PollVoteRepository pollVoteRepository;
    @Autowired private EntityManager entityManager;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    private List<User> authors;
    private User viewer;

    @BeforeEach
    void seedRealisticFeed() {
        viewer = newUser("feedcount_viewer");
        authors = new java.util.ArrayList<>();
        for (int i = 0; i < AUTHOR_COUNT; i++) {
            authors.add(newUser("feedcount_author_" + i));
        }

        for (int p = 0; p < POST_COUNT; p++) {
            User author = authors.get(p % AUTHOR_COUNT);
            Post post = new Post();
            post.setUser(author);
            post.setContent("feed-count post " + p);
            post.setVisibility(PostVisibility.PUBLIC);
            post = postRepository.save(post);

            if (p < MEDIA_POSTS) {
                MediaAsset asset = new MediaAsset();
                asset.setOwner(author);
                asset.setType(MediaType.IMAGE);
                asset.setPurpose(MediaPurpose.POST);
                asset.setUrl("/api/media/file/users/" + author.getId() + "/post/fake-" + p + ".png");
                asset.setStoragePath("users/" + author.getId() + "/post/fake-" + p + ".png");
                asset.setMimeType("image/png");
                asset.setFileSize(1024);
                asset = mediaAssetRepository.save(asset);
                PostMedia pm = new PostMedia();
                pm.setPost(post);
                pm.setMedia(asset);
                pm.setPosition(0);
                postMediaRepository.save(pm);
            }
            if (p < TOPIC_POSTS) {
                PostTopic pt = new PostTopic();
                pt.setPost(post);
                pt.setTopic(topic(p));
                postTopicRepository.save(pt);
            }
            if (p >= POST_COUNT - POLL_POSTS) {
                Poll poll = Poll.builder().post(post).question("poll " + p).allowMultiple(false).build();
                poll = pollRepository.save(poll);
                for (int o = 0; o < POLL_OPTIONS; o++) {
                    PollOption option = PollOption.builder()
                            .poll(poll).optionText("option " + o).position(o).voteCount(o < 2 ? 1 : 0).build();
                    option = pollOptionRepository.save(option);
                    if (o < 2) {
                        // two voters per poll, neither is the poll author
                        User voter = authors.get((p + o + 1) % AUTHOR_COUNT);
                        pollVoteRepository.save(PollVote.builder()
                                .poll(poll).option(option).user(voter).build());
                    }
                }
            }
        }
        // Count a realistic request: the service must resolve authors, media,
        // topics, polls and votes from the database, not from this test's
        // persistence context.
        entityManager.flush();
        entityManager.clear();
    }

    private Topic topic(int index) {
        String slug = "feed-count-topic-" + (index % 3);
        return topicRepository.findBySlug(slug).orElseGet(() -> {
            Topic t = new Topic();
            t.setName("Feed Count Topic " + (index % 3));
            t.setSlug(slug);
            return topicRepository.save(t);
        });
    }

    private User newUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@aloute.test");
        user.setPasswordHash("$2a$10$placeholder");
        user.setVerified(true);
        user.setActive(true);
        user = userRepository.save(user);
        Profile profile = new Profile();
        profile.setUser(user);
        profile.setDisplayName(username + " display");
        profileRepository.save(profile);
        return user;
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    /** PostResponse declares its poll component as Object; cast once here for the assertions. */
    private static com.aloute.dto.response.PollResponse pollOf(PostResponse post) {
        return (com.aloute.dto.response.PollResponse) post.poll();
    }

    private long countStatements(Runnable serviceCall) {
        Statistics stats = statistics();
        stats.clear();
        serviceCall.run();
        return stats.getPrepareStatementCount();
    }

    @Test
    @DisplayName("Authenticated feed page stays batched: profiles and poll votes are one query each, not per post/option")
    void authenticatedFeedPageStaysBatched() {
        var page = new java.util.concurrent.atomic.AtomicReference<List<PostResponse>>(null);
        long queries = countStatements(() -> page.set(postService.listPublic(0, POST_COUNT, viewer.getId()).data()));
        System.out.printf("[feed-query-count] authenticated listPublic(20 posts): %d statements%n", queries);

        // ~1 feed select + fixed visibility-spec lookups + 5 batched IN queries
        // + 1 profile batch + 1 poll batch + 1 poll-vote batch + one lazy author
        // init per distinct author. The pre-batching implementation needed ~55.
        assertThat(queries).as("JDBC statements for a 20-post authenticated feed page").isLessThanOrEqualTo(18);

        List<PostResponse> posts = page.get();
        assertThat(posts).hasSize(POST_COUNT);
        assertThat(posts.stream().filter(p -> p.media() != null && !p.media().isEmpty()).count()).isEqualTo(MEDIA_POSTS);
        assertThat(posts.stream().filter(p -> p.topics() != null && !p.topics().isEmpty()).count()).isEqualTo(TOPIC_POSTS);
        assertThat(posts.stream().filter(p -> p.poll() != null).count()).isEqualTo(POLL_POSTS);
        assertThat(posts).allSatisfy(p -> assertThat(p.author()).isNotNull());
        assertThat(posts).allSatisfy(p -> assertThat(p.author().id()).isNotNull());
        posts.stream().filter(p -> p.poll() != null).forEach(p -> {
            assertThat(pollOf(p).getOptions()).hasSize(POLL_OPTIONS);
            // the viewer has not voted: every option reports selected=false
            assertThat(pollOf(p).getOptions()).allSatisfy(o -> assertThat(o.getSelectedByCurrentUser()).isFalse());
        });
    }

    @Test
    @DisplayName("Anonymous feed page stays batched and leaves viewer flags null")
    void anonymousFeedPageStaysBatched() {
        var page = new java.util.concurrent.atomic.AtomicReference<List<PostResponse>>(null);
        long queries = countStatements(() -> page.set(postService.listPublic(0, POST_COUNT, null).data()));
        System.out.printf("[feed-query-count] anonymous listPublic(20 posts): %d statements%n", queries);

        assertThat(queries).as("JDBC statements for a 20-post anonymous feed page").isLessThanOrEqualTo(14);

        List<PostResponse> posts = page.get();
        assertThat(posts).hasSize(POST_COUNT);
        posts.stream().filter(p -> p.poll() != null)
                .forEach(p -> assertThat(pollOf(p).getOptions()).allSatisfy(o -> assertThat(o.getSelectedByCurrentUser()).isNull()));
    }

    @Test
    @DisplayName("Recommended feed page stays batched: follow state is one query, not one per post")
    void recommendedFeedPageStaysBatched() {
        var page = new java.util.concurrent.atomic.AtomicReference<List<PostResponse>>(null);
        long queries = countStatements(() -> page.set(postService.getRecommendedFeed(viewer.getId(), 0, POST_COUNT).data()));
        System.out.printf("[feed-query-count] authenticated getRecommendedFeed(20 posts): %d statements%n", queries);

        // feed data+count + visibility spec + one follow-graph query + the batched
        // toResponses queries. The pre-fix implementation probed followRepository
        // once per post (20 extra statements).
        assertThat(queries).as("JDBC statements for a 20-post recommended feed page").isLessThanOrEqualTo(20);

        assertThat(page.get()).hasSize(POST_COUNT);
    }

    @Test
    @DisplayName("Single poll mapping needs one options query plus one vote query, not one query per option")
    void singlePollMappingStaysConstant() {
        Long pollId = pollRepository.findByPost_IdIn(
                        postRepository.findAll().stream()
                                .filter(p -> ("feed-count post " + (POST_COUNT - POLL_POSTS)).equals(p.getContent()))
                                .map(Post::getId).toList())
                .get(0).getId();

        var response = new java.util.concurrent.atomic.AtomicReference<com.aloute.dto.response.PollResponse>(null);
        long queries = countStatements(() -> response.set(pollService.getPollById(pollId, viewer.getId())));
        System.out.printf("[feed-query-count] getPollById(4 options): %d statements%n", queries);

        // poll findById + options select + one vote select (was 1 + 1 + one exists per option)
        // BL-04: the detail path now enforces the canonical parent-post visibility gate,
        // a fixed cost independent of option count (1 post load + 1 author load +
        // 2 block probes + 1 follow probe). A per-option N+1 regression would exceed
        // this budget again (4 extra exists() probes).
        assertThat(queries).as("JDBC statements for poll detail with 4 options").isLessThanOrEqualTo(8);

        assertThat(response.get().getOptions()).hasSize(POLL_OPTIONS);
        assertThat(response.get().getOptions()).allSatisfy(o -> assertThat(o.getSelectedByCurrentUser()).isFalse());
        assertThat(response.get().getTotalVotes()).isEqualTo(2);
    }
}
