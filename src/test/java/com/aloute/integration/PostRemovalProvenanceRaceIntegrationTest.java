package com.aloute.integration;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.CreatePostRequest;
import com.aloute.dto.request.admin.AdminRemovePostRequest;
import com.aloute.dto.request.admin.AdminRestorePostRequest;
import com.aloute.dto.response.PostResponse;
import com.aloute.dto.response.admin.PostRemovalActionResponse;
import com.aloute.dto.response.admin.PostRestoreActionResponse;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.security.authorization.Role;
import com.aloute.service.PostService;
import com.aloute.service.admin.AdminModerationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:post_removal_race_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class PostRemovalProvenanceRaceIntegrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private PostService postService;
    @Autowired private AdminModerationService adminModerationService;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private PostModerationRemovalRepository postModerationRemovalRepository;
    @Autowired private ModerationActionRepository moderationActionRepository;
    @org.springframework.boot.test.mock.mockito.SpyBean private PostLikeRepository postLikeRepository;
    @org.springframework.boot.test.mock.mockito.SpyBean private RepostRepository repostRepository;
    @org.springframework.boot.test.mock.mockito.MockBean private com.aloute.listener.NotificationEventListener notificationEventListener;
    @Autowired private PlatformTransactionManager transactionManager;

    private User admin;
    private User author;
    private User interactionUser;

    private void assertInMemH2() {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            assertThat(url).as("Database must be ephemeral H2 in-memory").startsWith("jdbc:h2:mem:");
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify test datasource URL", e);
        }
    }

    private void cleanDatabase() {
        assertInMemH2();
        repostRepository.deleteAll();
        postLikeRepository.deleteAll();
        postModerationRemovalRepository.deleteAll();
        moderationActionRepository.deleteAll();
        postRepository.deleteAll();
        profileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
        cleanDatabase();

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager, def);

        txTemplate.executeWithoutResult(status -> {
            admin = new User();
            admin.setUsername("admin_provenance");
            admin.setEmail("admin_prov@example.com");
            admin.setPasswordHash("hash");
            admin.setRole(Role.SYSTEM_ADMIN);
            admin.setActive(true);
            admin = userRepository.save(admin);
            Profile adminProfile = new Profile();
            adminProfile.setUser(admin);
            adminProfile.setDisplayName("Admin Prov");
            profileRepository.save(adminProfile);

            author = new User();
            author.setUsername("author_user");
            author.setEmail("author@example.com");
            author.setPasswordHash("hash");
            author.setRole(Role.USER);
            author.setActive(true);
            author = userRepository.save(author);
            Profile authorProfile = new Profile();
            authorProfile.setUser(author);
            authorProfile.setDisplayName("Author User");
            profileRepository.save(authorProfile);

            interactionUser = new User();
            interactionUser.setUsername("interactor");
            interactionUser.setEmail("interactor@example.com");
            interactionUser.setPasswordHash("hash");
            interactionUser.setRole(Role.USER);
            interactionUser.setActive(true);
            interactionUser = userRepository.save(interactionUser);
            Profile interactorProfile = new Profile();
            interactorProfile.setUser(interactionUser);
            interactorProfile.setDisplayName("Interactor User");
            profileRepository.save(interactorProfile);
        });
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private Post createPublicPost(String content) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager, def);

        return txTemplate.execute(status -> {
            User authorInTx = userRepository.findById(author.getId()).orElseThrow();
            Post post = new Post();
            post.setUser(authorInTx);
            post.setContent(content);
            post.setVisibility(PostVisibility.PUBLIC);
            post.setAllowReply(true);
            post.setAllowQuote(true);
            post.setLikeCount(0);
            post.setRepostCount(0);
            post.setReplyCount(0);
            post.setQuoteCount(0);
            return postRepository.save(post);
        });
    }

    @Test
    @DisplayName("Concurrency: Like versus Admin removal serializes cleanly without resurrecting post")
    void concurrencyLikeVersusAdminRemovalSerializes() throws Exception {
        Post post = createPublicPost("Post to like concurrently with admin removal");
        Long postId = post.getId();

        CountDownLatch postLoadedLatch = new CountDownLatch(1);
        CountDownLatch adminAttemptLatch = new CountDownLatch(1);
        AtomicReference<Throwable> userError = new AtomicReference<>();
        AtomicReference<PostResponse> userResponse = new AtomicReference<>();

        doAnswer(inv -> {
            postLoadedLatch.countDown();
            assertThat(adminAttemptLatch.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(150);
            return false;
        }).when(postLikeRepository).existsByPostIdAndUserId(eq(postId), eq(interactionUser.getId()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> userFuture = executor.submit(() -> {
                try {
                    PostResponse resp = postService.like(interactionUser.getId(), postId);
                    userResponse.set(resp);
                } catch (Throwable t) {
                    userError.set(t);
                }
            });

            Future<?> adminFuture = executor.submit(() -> {
                try {
                    assertThat(postLoadedLatch.await(5, TimeUnit.SECONDS)).isTrue();
                    adminAttemptLatch.countDown();
                    adminModerationService.removePost(
                            admin.getId(),
                            postId,
                            new AdminRemovePostRequest(ReportReason.SPAM, "Violates community rules with spam", null, null)
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            userFuture.get(5, TimeUnit.SECONDS);
            adminFuture.get(5, TimeUnit.SECONDS);

            // Fresh Transaction verification
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionTemplate verifyTx = new TransactionTemplate(transactionManager, def);

            verifyTx.executeWithoutResult(status -> {
                Post postInDb = postRepository.findById(postId).orElseThrow();

                // 1. Persisted deletedAt must remain set (post was NOT resurrected)
                assertThat(postInDb.getDeletedAt()).as("Post must remain deleted").isNotNull();

                // 2. Exactly one active removal record
                PostModerationRemoval activeRemoval = postModerationRemovalRepository
                        .findFirstByPostIdAndRestoredAtIsNullOrderByIdDesc(postId)
                        .orElseThrow(() -> new AssertionError("Expected active removal record"));
                assertThat(activeRemoval.getRestoredAt()).isNull();

                // 3. Exactly one moderation action record
                assertThat(moderationActionRepository.findAll()).hasSize(1);

                // 4. Mutation committed first, then Admin removed: likeCount is 1
                assertThat(userError.get()).isNull();
                assertThat(userResponse.get()).isNotNull();
                assertThat(postInDb.getLikeCount()).isEqualTo(1);
                assertThat(postLikeRepository.count()).isEqualTo(1);

                // 5. Restore provenance remains fully functional
                PostRestoreActionResponse restoreResp = adminModerationService.restorePost(
                        admin.getId(),
                        postId,
                        new AdminRestorePostRequest(activeRemoval.getId(), "Restored after review")
                );
                assertThat(restoreResp).isNotNull();

                Post restoredPost = postRepository.findById(postId).orElseThrow();
                assertThat(restoredPost.getDeletedAt()).isNull();
                assertThat(restoredPost.getLikeCount()).isEqualTo(1);
                assertThat(postModerationRemovalRepository.existsByPostIdAndRestoredAtIsNull(postId)).isFalse();
            });
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("Concurrency: Repost versus Admin removal serializes cleanly without resurrecting post")
    void concurrencyRepostVersusAdminRemovalSerializes() throws Exception {
        Post post = createPublicPost("Post to repost concurrently with admin removal");
        Long postId = post.getId();

        CountDownLatch postLoadedLatch = new CountDownLatch(1);
        CountDownLatch adminAttemptLatch = new CountDownLatch(1);
        AtomicReference<Throwable> userError = new AtomicReference<>();
        AtomicReference<PostResponse> userResponse = new AtomicReference<>();

        doAnswer(inv -> {
            postLoadedLatch.countDown();
            assertThat(adminAttemptLatch.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(150);
            return false;
        }).when(repostRepository).existsByPostIdAndUserId(eq(postId), eq(interactionUser.getId()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> userFuture = executor.submit(() -> {
                try {
                    PostResponse resp = postService.repost(interactionUser.getId(), postId);
                    userResponse.set(resp);
                } catch (Throwable t) {
                    userError.set(t);
                }
            });

            Future<?> adminFuture = executor.submit(() -> {
                try {
                    assertThat(postLoadedLatch.await(5, TimeUnit.SECONDS)).isTrue();
                    adminAttemptLatch.countDown();
                    adminModerationService.removePost(
                            admin.getId(),
                            postId,
                            new AdminRemovePostRequest(ReportReason.HARASSMENT, "Harassment violation reported", null, null)
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            userFuture.get(5, TimeUnit.SECONDS);
            adminFuture.get(5, TimeUnit.SECONDS);

            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager, def);

            txTemplate.executeWithoutResult(status -> {
                Post postInDb = postRepository.findById(postId).orElseThrow();
                assertThat(postInDb.getDeletedAt()).as("Post must remain deleted").isNotNull();

                PostModerationRemoval activeRemoval = postModerationRemovalRepository
                        .findFirstByPostIdAndRestoredAtIsNullOrderByIdDesc(postId)
                        .orElseThrow();
                assertThat(activeRemoval.getRestoredAt()).isNull();
                assertThat(moderationActionRepository.findAll()).hasSize(1);

                assertThat(userError.get()).isNull();
                assertThat(userResponse.get()).isNotNull();
                assertThat(postInDb.getRepostCount()).isEqualTo(1);
                assertThat(repostRepository.count()).isEqualTo(1);

                // Restore provenance intact
                adminModerationService.restorePost(
                        admin.getId(),
                        postId,
                        new AdminRestorePostRequest(activeRemoval.getId(), "Restored after appeal")
                );
                Post restoredPost = postRepository.findById(postId).orElseThrow();
                assertThat(restoredPost.getDeletedAt()).isNull();
                assertThat(restoredPost.getRepostCount()).isEqualTo(1);
            });
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("Concurrency: Reply creation versus parent Admin removal serializes safely")
    void concurrencyReplyCreateVersusParentAdminRemoval() throws Exception {
        Post parent = createPublicPost("Parent post to reply to");
        Long parentId = parent.getId();

        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch adminAttemptLatch = new CountDownLatch(1);
        AtomicReference<Throwable> userError = new AtomicReference<>();
        AtomicReference<PostResponse> userResponse = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> userFuture = executor.submit(() -> {
                DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                TransactionTemplate tx = new TransactionTemplate(transactionManager, def);

                tx.executeWithoutResult(status -> {
                    try {
                        postRepository.findByIdForUpdate(parentId).orElseThrow();
                        lockAcquiredLatch.countDown();

                        assertThat(adminAttemptLatch.await(5, TimeUnit.SECONDS)).isTrue();
                        Thread.sleep(150);

                        CreatePostRequest req = new CreatePostRequest("Child reply content", PostVisibility.PUBLIC, true, true, null, null, null, null);
                        PostResponse resp = postService.create(interactionUser.getId(), req, null, parentId, null);
                        userResponse.set(resp);
                    } catch (Throwable t) {
                        userError.set(t);
                    }
                });
            });

            Future<?> adminFuture = executor.submit(() -> {
                try {
                    assertThat(lockAcquiredLatch.await(5, TimeUnit.SECONDS)).isTrue();
                    adminAttemptLatch.countDown();
                    adminModerationService.removePost(
                            admin.getId(),
                            parentId,
                            new AdminRemovePostRequest(ReportReason.HATE, "Hate violation in parent", null, null)
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            userFuture.get(5, TimeUnit.SECONDS);
            adminFuture.get(5, TimeUnit.SECONDS);

            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager, def);

            txTemplate.executeWithoutResult(status -> {
                Post parentInDb = postRepository.findById(parentId).orElseThrow();
                assertThat(parentInDb.getDeletedAt()).as("Parent post must remain deleted").isNotNull();

                PostModerationRemoval activeRemoval = postModerationRemovalRepository
                        .findFirstByPostIdAndRestoredAtIsNullOrderByIdDesc(parentId)
                        .orElseThrow();
                assertThat(activeRemoval.getRestoredAt()).isNull();
                assertThat(moderationActionRepository.findAll()).hasSize(1);

                assertThat(userError.get()).isNull();
                assertThat(userResponse.get()).isNotNull();
                assertThat(parentInDb.getReplyCount()).isEqualTo(1);

                // Restore parent
                adminModerationService.restorePost(
                        admin.getId(),
                        parentId,
                        new AdminRestorePostRequest(activeRemoval.getId(), "Restored parent")
                );
                Post restoredParent = postRepository.findById(parentId).orElseThrow();
                assertThat(restoredParent.getDeletedAt()).isNull();
                assertThat(restoredParent.getReplyCount()).isEqualTo(1);
            });
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("Concurrency: Quote creation versus quoted post Admin removal serializes safely")
    void concurrencyQuoteCreateVersusQuotedAdminRemoval() throws Exception {
        Post quoted = createPublicPost("Quoted post target");
        Long quotedId = quoted.getId();

        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch adminAttemptLatch = new CountDownLatch(1);
        AtomicReference<Throwable> userError = new AtomicReference<>();
        AtomicReference<PostResponse> userResponse = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> userFuture = executor.submit(() -> {
                DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                TransactionTemplate tx = new TransactionTemplate(transactionManager, def);

                tx.executeWithoutResult(status -> {
                    try {
                        postRepository.findByIdForUpdate(quotedId).orElseThrow();
                        lockAcquiredLatch.countDown();

                        assertThat(adminAttemptLatch.await(5, TimeUnit.SECONDS)).isTrue();
                        Thread.sleep(150);

                        CreatePostRequest req = new CreatePostRequest("Quote post content", PostVisibility.PUBLIC, true, true, null, null, null, null);
                        PostResponse resp = postService.create(interactionUser.getId(), req, null, null, quotedId);
                        userResponse.set(resp);
                    } catch (Throwable t) {
                        userError.set(t);
                    }
                });
            });

            Future<?> adminFuture = executor.submit(() -> {
                try {
                    assertThat(lockAcquiredLatch.await(5, TimeUnit.SECONDS)).isTrue();
                    adminAttemptLatch.countDown();
                    adminModerationService.removePost(
                            admin.getId(),
                            quotedId,
                            new AdminRemovePostRequest(ReportReason.MISINFORMATION, "Misinformation in quoted post", null, null)
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            userFuture.get(5, TimeUnit.SECONDS);
            adminFuture.get(5, TimeUnit.SECONDS);

            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager, def);

            txTemplate.executeWithoutResult(status -> {
                Post quotedInDb = postRepository.findById(quotedId).orElseThrow();
                assertThat(quotedInDb.getDeletedAt()).as("Quoted post must remain deleted").isNotNull();

                PostModerationRemoval activeRemoval = postModerationRemovalRepository
                        .findFirstByPostIdAndRestoredAtIsNullOrderByIdDesc(quotedId)
                        .orElseThrow();
                assertThat(activeRemoval.getRestoredAt()).isNull();
                assertThat(moderationActionRepository.findAll()).hasSize(1);

                assertThat(userError.get()).isNull();
                assertThat(userResponse.get()).isNotNull();
                assertThat(quotedInDb.getQuoteCount()).isEqualTo(1);

                // Restore quoted post
                adminModerationService.restorePost(
                        admin.getId(),
                        quotedId,
                        new AdminRestorePostRequest(activeRemoval.getId(), "Restored quoted post")
                );
                Post restoredQuoted = postRepository.findById(quotedId).orElseThrow();
                assertThat(restoredQuoted.getDeletedAt()).isNull();
                assertThat(restoredQuoted.getQuoteCount()).isEqualTo(1);
            });
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("Concurrency: Delete reply versus parent Admin removal preserves parent deletedAt")
    void concurrencyDeleteReplyVersusParentAdminRemoval() throws Exception {
        Post parent = createPublicPost("Parent with reply to delete");
        Long parentId = parent.getId();

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionTemplate setupTx = new TransactionTemplate(transactionManager, def);

        Long replyId = setupTx.execute(status -> {
            User authorInTx = userRepository.findById(author.getId()).orElseThrow();
            Post parentInTx = postRepository.findById(parentId).orElseThrow();
            parentInTx.setReplyCount(1);
            postRepository.save(parentInTx);

            Post reply = new Post();
            reply.setUser(authorInTx);
            reply.setContent("Reply to be deleted");
            reply.setVisibility(PostVisibility.PUBLIC);
            reply.setAllowReply(true);
            reply.setAllowQuote(true);
            reply.setParentPost(parentInTx);
            return postRepository.save(reply).getId();
        });

        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch adminAttemptLatch = new CountDownLatch(1);
        AtomicReference<Throwable> deleteError = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> deleteFuture = executor.submit(() -> {
                DefaultTransactionDefinition def2 = new DefaultTransactionDefinition();
                def2.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                TransactionTemplate tx = new TransactionTemplate(transactionManager, def2);

                tx.executeWithoutResult(status -> {
                    try {
                        postRepository.findByIdForUpdate(parentId).orElseThrow();
                        lockAcquiredLatch.countDown();

                        assertThat(adminAttemptLatch.await(5, TimeUnit.SECONDS)).isTrue();
                        Thread.sleep(150);

                        postService.delete(author.getId(), replyId);
                    } catch (Throwable t) {
                        deleteError.set(t);
                    }
                });
            });

            Future<?> adminFuture = executor.submit(() -> {
                try {
                    assertThat(lockAcquiredLatch.await(5, TimeUnit.SECONDS)).isTrue();
                    adminAttemptLatch.countDown();
                    adminModerationService.removePost(
                            admin.getId(),
                            parentId,
                            new AdminRemovePostRequest(ReportReason.VIOLENCE, "Violence in parent post content", null, null)
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            deleteFuture.get(5, TimeUnit.SECONDS);
            adminFuture.get(5, TimeUnit.SECONDS);

            setupTx.executeWithoutResult(status -> {
                Post parentInDb = postRepository.findById(parentId).orElseThrow();
                Post replyInDb = postRepository.findById(replyId).orElseThrow();

                // Reply is deleted
                assertThat(replyInDb.getDeletedAt()).isNotNull();

                // Parent must remain deleted (NEVER overwritten with null by delete-reply counter decrement)
                assertThat(parentInDb.getDeletedAt()).as("Parent post must remain deleted").isNotNull();
                assertThat(parentInDb.getReplyCount()).isEqualTo(0);

                // Active removal intact
                PostModerationRemoval activeRemoval = postModerationRemovalRepository
                        .findFirstByPostIdAndRestoredAtIsNullOrderByIdDesc(parentId)
                        .orElseThrow();
                assertThat(activeRemoval.getRestoredAt()).isNull();
                assertThat(moderationActionRepository.findAll()).hasSize(1);

                assertThat(deleteError.get()).isNull();

                // Restore parent
                adminModerationService.restorePost(
                        admin.getId(),
                        parentId,
                        new AdminRestorePostRequest(activeRemoval.getId(), "Restored parent after investigation")
                );
                Post restoredParent = postRepository.findById(parentId).orElseThrow();
                assertThat(restoredParent.getDeletedAt()).isNull();
                assertThat(restoredParent.getReplyCount()).isEqualTo(0);
            });
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
