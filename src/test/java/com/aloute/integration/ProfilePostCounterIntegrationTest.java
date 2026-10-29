package com.aloute.integration;

import com.aloute.common.exception.ApiException;
import com.aloute.dto.request.CreatePostRequest;
import com.aloute.entity.Community;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.CommunityRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.service.PostService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BL-15 (issue #92): the profile and community post counters are LIFETIME root-post
 * counters. Every successfully persisted root post (quotes and community posts count,
 * replies do not) advances them exactly once through an atomic bulk UPDATE, so
 * concurrent creations cannot lose increments, soft delete never decrements them,
 * edits never re-increment them and a rolled-back creation leaves them untouched.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class ProfilePostCounterIntegrationTest {

    @Autowired private PostService postService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private CommunityRepository communityRepository;
    @Autowired private com.aloute.repository.PostRepository postRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private com.aloute.repository.CommunityMemberRepository communityMemberRepository;
    @Autowired private javax.sql.DataSource dataSource;

    private User author;
    private List<Long> createdUserIds = new ArrayList<>();
    private Long createdCommunityId;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        author = newUser("bl15_" + suffix);
    }

    @AfterEach
    void cleanUp() {
        // Direct row deletion (the lifetime counters under test are never decremented
        // through this path). Posts are removed children-first, repeatedly, so
        // parent/quote foreign keys never dangle.
        for (Long id : createdUserIds) {
            for (int pass = 0; pass < 10; pass++) {
                List<com.aloute.entity.Post> remaining = postRepository.findAll().stream()
                        .filter(p -> p.getUser() != null && id.equals(p.getUser().getId()))
                        .toList();
                if (remaining.isEmpty()) {
                    break;
                }
                remaining.stream()
                        .filter(p -> p.getParentPost() != null)
                        .forEach(p -> postRepository.deleteById(p.getId()));
                remaining.stream()
                        .filter(p -> p.getParentPost() == null && p.getQuotedPost() != null)
                        .forEach(p -> postRepository.deleteById(p.getId()));
                remaining.stream()
                        .filter(p -> p.getParentPost() == null && p.getQuotedPost() == null)
                        .forEach(p -> postRepository.deleteById(p.getId()));
            }
            postRepository.flush();
        }
        if (createdCommunityId != null) {
            communityMemberRepository.findByCommunityIdOrderByJoinedAtDesc(createdCommunityId, org.springframework.data.domain.Pageable.unpaged())
                    .forEach(member -> communityMemberRepository.deleteById(member.getId()));
            communityRepository.deleteById(createdCommunityId);
        }
        for (Long id : createdUserIds) {
            profileRepository.deleteById(id);
            userRepository.deleteById(id);
        }
    }

    private User newUser(String username) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@aloute.test");
        u.setPasswordHash(passwordEncoder.encode("Correct@Password1"));
        u.setVerified(true);
        u.setActive(true);
        u.setRole(Role.USER);
        final User saved = transactionTemplate.execute(status -> {
            User managed = userRepository.saveAndFlush(u);
            Profile p = new Profile();
            p.setUser(managed);
            p.setDisplayName(username);
            p.setPostsCount(0);
            profileRepository.saveAndFlush(p);
            return managed;
        });
        createdUserIds.add(saved.getId());
        return saved;
    }

    private void createPost(User as, String content) {
        transactionTemplate.executeWithoutResult(status ->
                postService.create(as.getId(), new CreatePostRequest(content, null, null, null, null, null, null, null),
                        null, null, null));
    }

    @Test
    @DisplayName("create 1 root post → postsCount +1; create several → exactly one increment each")
    void oneIncrementPerRootPost() {
        assertThat(profileRepository.findById(author.getId()).orElseThrow().getPostsCount()).isZero();
        createPost(author, "first root post");
        assertThat(profileRepository.findById(author.getId()).orElseThrow().getPostsCount()).isEqualTo(1);
        createPost(author, "second root post");
        createPost(author, "third root post");
        assertThat(profileRepository.findById(author.getId()).orElseThrow().getPostsCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("replies and quotes: replies do not count, quotes (root posts) do")
    void repliesExcludedQuotesIncluded() {
        createPost(author, "root post");
        long rootId = postRepository.findAll().stream()
                .filter(p -> author.getId().equals(p.getUser().getId()) && p.getParentPost() == null)
                .findFirst().orElseThrow().getId();

        transactionTemplate.executeWithoutResult(status ->
                postService.create(author.getId(), new CreatePostRequest("a reply", null, null, null, null, null, null, null),
                        null, rootId, null));
        assertThat(profileRepository.findById(author.getId()).orElseThrow().getPostsCount())
                .as("a reply is not a root post and must not advance the lifetime counter")
                .isEqualTo(1);

        transactionTemplate.executeWithoutResult(status ->
                postService.create(author.getId(), new CreatePostRequest("a quote", null, null, null, null, null, null, null),
                        null, null, rootId));
        assertThat(profileRepository.findById(author.getId()).orElseThrow().getPostsCount())
                .as("a quote is a root post and advances the lifetime counter once")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("soft delete does not decrement the lifetime counter; edit does not re-increment")
    void softDeleteAndEditKeepLifetimeCounter() {
        createPost(author, "to be deleted");
        long postId = postRepository.findAll().stream()
                .filter(p -> author.getId().equals(p.getUser().getId()))
                .findFirst().orElseThrow().getId();

        transactionTemplate.executeWithoutResult(status -> postService.delete(author.getId(), postId));
        assertThat(profileRepository.findById(author.getId()).orElseThrow().getPostsCount())
                .as("lifetime counter never decrements on soft delete")
                .isEqualTo(1);

        createPost(author, "kept post");
        long keptId = postRepository.findAll().stream()
                .filter(p -> author.getId().equals(p.getUser().getId())
                        && p.getDeletedAt() == null && p.getParentPost() == null)
                .findFirst().orElseThrow().getId();
        transactionTemplate.executeWithoutResult(status ->
                postService.update(author.getId(), keptId,
                        new com.aloute.dto.request.UpdatePostRequest("edited content, still one post", null, null, null)));
        assertThat(profileRepository.findById(author.getId()).orElseThrow().getPostsCount())
                .as("editing a post must not re-increment the counter")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("rolled-back creation does not advance the counter")
    void rolledBackCreationDoesNotCount() {
        long before = profileRepository.findById(author.getId()).orElseThrow().getPostsCount();
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                postService.create(author.getId(),
                        new CreatePostRequest("doomed media post", null, null, null, List.of(999_999_999L), null, null, null),
                        null, null, null)))
                .isInstanceOf(ApiException.class);
        assertThat(profileRepository.findById(author.getId()).orElseThrow().getPostsCount())
                .as("the whole create transaction rolled back, counter must stay untouched")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("community root post advances both profile and community counters; community reply advances neither")
    void communityRootPostAdvancesBothCounters() {
        long suffix = System.nanoTime();
        Community community = new Community();
        community.setName("bl15-community-" + suffix);
        community.setSlug("bl15-community-" + suffix);
        community.setDescription("counter test");
        community.setOwner(author);
        community.setMemberCount(1);
        community.setPostCount(0);
        final Community saved = transactionTemplate.execute(status -> {
            Community c = communityRepository.saveAndFlush(community);
            com.aloute.entity.CommunityMember member = new com.aloute.entity.CommunityMember(c, author, com.aloute.entity.CommunityRole.ADMIN);
            communityMemberRepository.saveAndFlush(member);
            createdCommunityId = c.getId();
            return c;
        });

        transactionTemplate.executeWithoutResult(status ->
                postService.create(author.getId(), new CreatePostRequest("community root", null, null, null, null, null, null, null),
                        saved.getId(), null, null));
        assertThat(profileRepository.findById(author.getId()).orElseThrow().getPostsCount()).isEqualTo(1);
        assertThat(communityRepository.findById(saved.getId()).orElseThrow().getPostCount()).isEqualTo(1);

        long communityRootId = postRepository.findAll().stream()
                .filter(p -> saved.getId().equals(p.getCommunity() == null ? null : p.getCommunity().getId()))
                .findFirst().orElseThrow().getId();
        transactionTemplate.executeWithoutResult(status ->
                postService.create(author.getId(), new CreatePostRequest("community reply", null, null, null, null, null, null, null),
                        saved.getId(), communityRootId, null));
        assertThat(profileRepository.findById(author.getId()).orElseThrow().getPostsCount())
                .as("a reply inside the community does not advance the profile counter")
                .isEqualTo(1);
        assertThat(communityRepository.findById(saved.getId()).orElseThrow().getPostCount())
                .as("a reply inside the community does not advance the community counter")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("backfill script recomputes both lifetime counters from posts and is idempotent")
    void backfillScriptRecomputesAndIsIdempotent() throws Exception {
        // Drift the stored counters away from the truth.
        Profile drifted = profileRepository.findById(author.getId()).orElseThrow();
        drifted.setPostsCount(999);
        profileRepository.save(drifted);

        createPost(author, "backfill root post");
        long rootId = postRepository.findAll().stream()
                .filter(p -> author.getId().equals(p.getUser().getId()) && p.getParentPost() == null)
                .findFirst().orElseThrow().getId();
        transactionTemplate.executeWithoutResult(status ->
                postService.create(author.getId(), new CreatePostRequest("backfill reply", null, null, null, null, null, null, null),
                        null, rootId, null));

        // Run the H2 twin of the MySQL 8 backfill: twice, to prove idempotence.
        try (var conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/h2_backfill_post_counters.sql"));
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/h2_backfill_post_counters.sql"));
        }

        assertThat(profileRepository.findById(author.getId()).orElseThrow().getPostsCount())
                .as("backfill counts lifetime ROOT posts only (the reply is excluded)")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("parallel root-post creation through separate transactions never loses increments")
    void parallelCreationDoesNotLoseUpdates() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(pool.submit((Callable<Integer>) () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    transactionTemplate.executeWithoutResult(status ->
                            postService.create(author.getId(),
                                    new CreatePostRequest("parallel post " + idx, null, null, null, null, null, null, null),
                                    null, null, null));
                    return 1;
                }));
            }
            int completed = 0;
            for (Future<Integer> future : futures) {
                future.get(30, TimeUnit.SECONDS);
                completed++;
            }
            assertThat(completed).isEqualTo(threads);
        } finally {
            pool.shutdownNow();
        }
        assertThat(profileRepository.findById(author.getId()).orElseThrow().getPostsCount())
                .as("every parallel creation must survive the counter increment")
                .isEqualTo(threads);
    }
}
