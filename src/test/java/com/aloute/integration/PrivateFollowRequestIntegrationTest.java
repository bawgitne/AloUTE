package com.aloute.integration;

import com.aloute.common.exception.ApiException;
import com.aloute.entity.FollowRequest;
import com.aloute.entity.FollowRequestStatus;
import com.aloute.entity.Notification;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.FollowRepository;
import com.aloute.repository.FollowRequestRepository;
import com.aloute.repository.NotificationRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.SocialService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
 * BL-05 (issue #92): the private-account follow-request lifecycle.
 *
 * Covers the full state machine (request → approve/reject/cancel), authorization
 * (self, target, third party, guest-adjacent rules), block interactions, the
 * private→public invalidation policy, idempotent re-requests, concurrency safety
 * of duplicate requests and racing approve/cancel, and the guarantee that a PENDING
 * request never grants private-post visibility or moves any counter.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class PrivateFollowRequestIntegrationTest {

    @Autowired private SocialService socialService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private FollowRepository followRepository;
    @Autowired private FollowRequestRepository followRequestRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private com.aloute.repository.BlockRepository blockRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private com.aloute.service.PostService postService;
    @Autowired private com.aloute.service.UserService userService;
    @Autowired private com.aloute.repository.PostRepository postRepository;

    private User alice;   // private-account owner (target)
    private User bob;     // requester
    private User carol;   // third party
    private List<Long> createdUserIds = new ArrayList<>();
    private List<Long> createdPostIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        alice = newUser("bl05alice_" + suffix, true);
        bob = newUser("bl05bob_" + suffix, false);
        carol = newUser("bl05carol_" + suffix, false);
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        // The AFTER_COMMIT notification listener is @Async and may still be inserting
        // rows that reference the created users; give it a moment, then remove
        // dependent rows (repeating briefly) before the users themselves.
        Thread.sleep(1200);
        for (int attempt = 0; attempt < 8; attempt++) {
            try {
                deleteDependentRows();
                return;
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                Thread.sleep(800);
            }
        }
        deleteDependentRows();
    }

    private void deleteDependentRows() {
        transactionTemplate.executeWithoutResult(status -> {
            // Notifications and blocks reference the created users; remove them first
            // or the user deletion trips the foreign keys.
            notificationRepository.findAll().forEach(notification -> {
                boolean recipientIsOurs = notification.getRecipient() != null
                        && createdUserIds.contains(notification.getRecipient().getId());
                boolean actorIsOurs = notification.getActor() != null
                        && createdUserIds.contains(notification.getActor().getId());
                if (recipientIsOurs || actorIsOurs) {
                    notificationRepository.delete(notification);
                }
            });
            blockRepository.findAll().forEach(block -> {
                if (createdUserIds.contains(block.getBlocker().getId())
                        || createdUserIds.contains(block.getBlocked().getId())) {
                    blockRepository.delete(block);
                }
            });
            followRequestRepository.findAll().forEach(request -> {
                if (createdUserIds.contains(request.getRequester().getId())
                        || createdUserIds.contains(request.getTarget().getId())) {
                    followRequestRepository.delete(request);
                }
            });
            followRepository.findAll().forEach(follow -> {
                if (createdUserIds.contains(follow.getFollower().getId())
                        || createdUserIds.contains(follow.getFollowing().getId())) {
                    followRepository.delete(follow);
                }
            });
            // Posts created by the test reference the users (and each other).
            for (Long id : createdUserIds) {
                for (int pass = 0; pass < 6; pass++) {
                    List<com.aloute.entity.Post> remaining = postRepository.findAll().stream()
                            .filter(post -> post.getUser() != null && id.equals(post.getUser().getId()))
                            .toList();
                    if (remaining.isEmpty()) {
                        break;
                    }
                    remaining.stream().filter(post -> post.getParentPost() != null)
                            .forEach(post -> postRepository.deleteById(post.getId()));
                    remaining.stream().filter(post -> post.getParentPost() == null && post.getQuotedPost() != null)
                            .forEach(post -> postRepository.deleteById(post.getId()));
                    remaining.stream().filter(post -> post.getParentPost() == null && post.getQuotedPost() == null)
                            .forEach(post -> postRepository.deleteById(post.getId()));
                }
            }
            for (Long id : createdUserIds) {
                profileRepository.deleteById(id);
                userRepository.deleteById(id);
            }
        });
    }

    private User newUser(String username, boolean privateAccount) {
        final User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@aloute.test");
        u.setPasswordHash(passwordEncoder.encode("Correct@Password1"));
        u.setVerified(true);
        u.setActive(true);
        u.setRole(Role.USER);
        u.setPrivateAccount(privateAccount);
        final User saved = transactionTemplate.execute(status -> {
            User managed = userRepository.saveAndFlush(u);
            Profile p = new Profile();
            p.setUser(managed);
            p.setDisplayName(username);
            profileRepository.saveAndFlush(p);
            return managed;
        });
        createdUserIds.add(saved.getId());
        return saved;
    }

    private void unblockPair() {
        transactionTemplate.executeWithoutResult(status -> {
            blockRepository.findByBlockerIdAndBlockedId(bob.getId(), alice.getId()).ifPresent(blockRepository::delete);
            blockRepository.findByBlockerIdAndBlockedId(alice.getId(), bob.getId()).ifPresent(blockRepository::delete);
        });
    }

    private FollowRequest createRequest(User from, User to) {
        return transactionTemplate.execute(status -> {
            socialService.follow(from.getId(), to.getId());
            return followRequestRepository.findByRequesterIdAndTargetId(from.getId(), to.getId()).orElseThrow();
        });
    }

    private void awaitNotificationType(Long recipientId, String type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            boolean found = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(recipientId, Pageable.unpaged())
                    .getContent().stream()
                    .anyMatch(n -> type.equals(n.getType()));
            if (found) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("notification of type " + type + " did not arrive for user " + recipientId);
    }

    @Test
    @DisplayName("following a private account creates exactly one PENDING request: no follow row, no counter movement")
    void requestLifecycleCreatesPendingOnly() {
        createRequest(bob, alice);

        assertThat(followRepository.existsById(new com.aloute.entity.FollowId(bob.getId(), alice.getId())))
                .as("no Follow relationship may exist before approval")
                .isFalse();
        assertThat(profileRepository.findById(bob.getId()).orElseThrow().getFollowingCount()).isZero();
        assertThat(profileRepository.findById(alice.getId()).orElseThrow().getFollowersCount()).isZero();

        FollowRequest request = followRequestRepository.findByRequesterIdAndTargetId(bob.getId(), alice.getId()).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(FollowRequestStatus.PENDING);

        var relation = socialService.getRelationStatus(bob.getId(), alice.getId());
        assertThat(relation.following()).isFalse();
        assertThat(relation.followRequestPending()).isTrue();
    }

    @Test
    @DisplayName("double click / repeated follows converge on one single request row")
    void duplicateRequestsConvergeOnOneRow() throws Exception {
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit((Callable<Integer>) () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    transactionTemplate.executeWithoutResult(status -> socialService.follow(bob.getId(), alice.getId()));
                    return 1;
                }));
            }
            for (Future<Integer> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException ex) {
                    // A true simultaneous race past the pre-check loses on the unique
                    // pair constraint with a clean 409 — a valid double-click outcome.
                    Throwable cause = ex.getCause();
                    assertThat(cause).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) cause).getCode()).isEqualTo("FOLLOW_REQUEST_ALREADY_PENDING");
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(followRequestRepository.findByRequesterIdAndTargetId(bob.getId(), alice.getId()).orElseThrow().getStatus())
                .isEqualTo(FollowRequestStatus.PENDING);
    }

    @Test
    @DisplayName("approve: creates the follow exactly once, moves both counters once, notifies the requester")
    void approveCreatesFollowAndCounters() throws Exception {
        FollowRequest request = createRequest(bob, alice);

        transactionTemplate.executeWithoutResult(status -> socialService.approveFollowRequest(alice.getId(), request.getId()));

        assertThat(followRepository.existsById(new com.aloute.entity.FollowId(bob.getId(), alice.getId()))).isTrue();
        assertThat(profileRepository.findById(bob.getId()).orElseThrow().getFollowingCount()).isEqualTo(1);
        assertThat(profileRepository.findById(alice.getId()).orElseThrow().getFollowersCount()).isEqualTo(1);
        assertThat(socialService.getRelationStatus(bob.getId(), alice.getId()).following()).isTrue();
        assertThat(socialService.getRelationStatus(bob.getId(), alice.getId()).followRequestPending()).isFalse();

        awaitNotificationType(alice.getId(), "FOLLOW_REQUEST");
        awaitNotificationType(bob.getId(), "FOLLOW_ACCEPTED");

        // Approving twice must not double-move anything.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                socialService.approveFollowRequest(alice.getId(), request.getId())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("FOLLOW_REQUEST_NOT_PENDING"));
        assertThat(profileRepository.findById(alice.getId()).orElseThrow().getFollowersCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("reject: no follow row is created and the requester may ask again later")
    void rejectDoesNotCreateFollowAndAllowsReRequest() {
        FollowRequest request = createRequest(bob, alice);
        transactionTemplate.executeWithoutResult(status -> socialService.rejectFollowRequest(alice.getId(), request.getId()));

        assertThat(followRepository.existsById(new com.aloute.entity.FollowId(bob.getId(), alice.getId()))).isFalse();
        assertThat(profileRepository.findById(alice.getId()).orElseThrow().getFollowersCount()).isZero();
        assertThat(socialService.getRelationStatus(bob.getId(), alice.getId()).followRequestPending()).isFalse();

        // Re-request re-arms the same row to PENDING.
        transactionTemplate.executeWithoutResult(status -> socialService.follow(bob.getId(), alice.getId()));
        assertThat(followRequestRepository.findByRequesterIdAndTargetId(bob.getId(), alice.getId()).orElseThrow().getStatus())
                .isEqualTo(FollowRequestStatus.PENDING);
    }

    @Test
    @DisplayName("cancel: the requester withdraws the pending request; approve afterwards is refused")
    void cancelThenApproveIsRefused() {
        FollowRequest request = createRequest(bob, alice);
        transactionTemplate.executeWithoutResult(status -> socialService.unfollow(bob.getId(), alice.getId()));

        assertThat(followRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(FollowRequestStatus.CANCELED);
        assertThat(socialService.getRelationStatus(bob.getId(), alice.getId()).followRequestPending()).isFalse();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                socialService.approveFollowRequest(alice.getId(), request.getId())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("FOLLOW_REQUEST_NOT_PENDING"));
    }

    @Test
    @DisplayName("authorization: only the target acts on a request; requester and third party are refused")
    void onlyTargetCanActOnRequest() {
        FollowRequest request = createRequest(bob, alice);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                socialService.approveFollowRequest(bob.getId(), request.getId())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("FOLLOW_REQUEST_NOT_OWNED"));
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                socialService.rejectFollowRequest(carol.getId(), request.getId())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("FOLLOW_REQUEST_NOT_OWNED"));
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                socialService.approveFollowRequest(carol.getId(), request.getId())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("FOLLOW_REQUEST_NOT_OWNED"));
    }

    @Test
    @DisplayName("pending list: the target sees the request; other users see only their own inbox")
    void pendingListIsScopedToTheOwner() {
        createRequest(bob, alice);

        var aliceInbox = socialService.pendingFollowRequests(alice.getId(), PageRequest.of(0, 20));
        assertThat(aliceInbox.getData()).hasSize(1);
        assertThat(aliceInbox.getData().get(0).requester().id()).isEqualTo(bob.getId());

        assertThat(socialService.pendingFollowRequests(carol.getId(), PageRequest.of(0, 20)).getData()).isEmpty();
        assertThat(socialService.pendingFollowRequests(bob.getId(), PageRequest.of(0, 20)).getData()).isEmpty();
    }

    @Test
    @DisplayName("block: cannot create a request between blocked pairs and block() cancels an outstanding request")
    void blockPreventsAndSeversRequests() {
        FollowRequest request = createRequest(bob, alice);

        // Blocking severs the outstanding request in either direction.
        transactionTemplate.executeWithoutResult(status -> socialService.block(alice.getId(), bob.getId()));
        assertThat(followRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(FollowRequestStatus.CANCELED);
        unblockPair();

        // A fresh request between blocked users is refused — in BOTH block directions.
        transactionTemplate.executeWithoutResult(status -> socialService.block(alice.getId(), bob.getId()));
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                socialService.follow(bob.getId(), alice.getId())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("BLOCKED_BY_RELATIONSHIP"));
        unblockPair();
        transactionTemplate.executeWithoutResult(status -> socialService.block(bob.getId(), alice.getId()));
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                socialService.follow(bob.getId(), alice.getId())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("BLOCKED_BY_RELATIONSHIP"));
        // Note: the direct public-follow path keeps the existing no-block-check policy
        // (out of BL-05 scope); only the private-request path refuses blocked pairs.
    }

    @Test
    @DisplayName("pending request grants NO visibility to the private author's posts; approval does")
    void pendingRequestGrantsNoVisibility() {
        long postId = transactionTemplate.execute(status -> {
            try {
                var created = postService.create(alice.getId(),
                        new com.aloute.dto.request.CreatePostRequest("private secret post", null, null, null, null, null, null, null),
                        null, null, null);
                createdPostIds.add(created.id());
                return created.id();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        createRequest(bob, alice);
        assertThatThrownBy(() -> postService.get(postId, bob.getId()))
                .as("a PENDING request must not reveal the private author's post")
                .isInstanceOf(ApiException.class);

        FollowRequest request = followRequestRepository.findByRequesterIdAndTargetId(bob.getId(), alice.getId()).orElseThrow();
        transactionTemplate.executeWithoutResult(status -> socialService.approveFollowRequest(alice.getId(), request.getId()));
        assertThat(postService.get(postId, bob.getId()))
                .as("an approved follower may read the private author's post")
                .isNotNull();
    }

    @Test
    @DisplayName("private→public switch invalidates pending requests instead of auto-approving them")
    void privateToPublicSwitchInvalidatesPending() {
        FollowRequest request = createRequest(bob, alice);

        transactionTemplate.executeWithoutResult(status -> {
            com.aloute.dto.request.UpdateProfileRequest switchToPublic =
                    new com.aloute.dto.request.UpdateProfileRequest();
            switchToPublic.setPrivateAccount(false);
            userService.updateCurrentProfile(alice.getId(), switchToPublic);
        });

        assertThat(followRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                .as("the backlog must be canceled, never silently approved")
                .isEqualTo(FollowRequestStatus.CANCELED);
        assertThat(followRepository.existsById(new com.aloute.entity.FollowId(bob.getId(), alice.getId()))).isFalse();

        // After the switch, following the now-public account works directly and the
        // stale canceled row does not shadow it.
        transactionTemplate.executeWithoutResult(status -> socialService.follow(bob.getId(), alice.getId()));
        assertThat(followRepository.existsById(new com.aloute.entity.FollowId(bob.getId(), alice.getId()))).isTrue();
        assertThat(profileRepository.findById(alice.getId()).orElseThrow().getFollowersCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("public→private switch keeps existing followers; new follows must request")
    void publicToPrivateKeepsFollowers() {
        // Arrange: alice is public with an approved follower.
        transactionTemplate.executeWithoutResult(status -> {
            User reloaded = userRepository.findById(alice.getId()).orElseThrow();
            reloaded.setPrivateAccount(false);
            userRepository.save(reloaded);
        });
        transactionTemplate.executeWithoutResult(status -> socialService.follow(bob.getId(), alice.getId()));
        assertThat(followRepository.existsById(new com.aloute.entity.FollowId(bob.getId(), alice.getId()))).isTrue();

        transactionTemplate.executeWithoutResult(status -> {
            com.aloute.dto.request.UpdateProfileRequest switchToPrivate =
                    new com.aloute.dto.request.UpdateProfileRequest();
            switchToPrivate.setPrivateAccount(true);
            userService.updateCurrentProfile(alice.getId(), switchToPrivate);
        });

        assertThat(followRepository.existsById(new com.aloute.entity.FollowId(bob.getId(), alice.getId())))
                .as("existing followers survive the switch")
                .isTrue();
        transactionTemplate.executeWithoutResult(status -> socialService.follow(carol.getId(), alice.getId()));
        assertThat(socialService.getRelationStatus(carol.getId(), alice.getId()).followRequestPending()).isTrue();
    }

    @Test
    @DisplayName("public accounts keep the direct follow path with atomic counter movement")
    void publicFollowPathUnchanged() {
        transactionTemplate.executeWithoutResult(status -> socialService.follow(bob.getId(), carol.getId()));
        assertThat(followRepository.existsById(new com.aloute.entity.FollowId(bob.getId(), carol.getId()))).isTrue();
        assertThat(profileRepository.findById(bob.getId()).orElseThrow().getFollowingCount()).isEqualTo(1);
        assertThat(profileRepository.findById(carol.getId()).orElseThrow().getFollowersCount()).isEqualTo(1);

        transactionTemplate.executeWithoutResult(status -> socialService.unfollow(bob.getId(), carol.getId()));
        assertThat(profileRepository.findById(bob.getId()).orElseThrow().getFollowingCount()).isZero();
        assertThat(profileRepository.findById(carol.getId()).orElseThrow().getFollowersCount()).isZero();
    }

    @Test
    @DisplayName("racing approve and cancel: exactly one wins and the final state is consistent")
    void racingApproveAndCancelAreConsistent() throws Exception {
        FollowRequest request = createRequest(bob, alice);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<?> approver = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            try { barrier.await(5, TimeUnit.SECONDS); } catch (Exception ignored) { }
            socialService.approveFollowRequest(alice.getId(), request.getId());
        }));
        Future<?> canceller = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            try { barrier.await(5, TimeUnit.SECONDS); } catch (Exception ignored) { }
            socialService.unfollow(bob.getId(), alice.getId());
        }));
        try {
            ApiException loser = null;
            try {
                approver.get(30, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException ex) {
                loser = (ApiException) ex.getCause();
            }
            try {
                canceller.get(30, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException ex) {
                loser = (ApiException) ex.getCause();
            }
            // The loser must have failed with FOLLOW_REQUEST_NOT_PENDING.
            if (loser != null) {
                assertThat(loser.getCode()).isEqualTo("FOLLOW_REQUEST_NOT_PENDING");
            }

            FollowRequestStatus finalStatus = followRequestRepository.findById(request.getId()).orElseThrow().getStatus();
            boolean followExists = followRepository.existsById(new com.aloute.entity.FollowId(bob.getId(), alice.getId()));
            if (finalStatus == FollowRequestStatus.ACCEPTED) {
                assertThat(followExists).as("accepted request implies the follow exists").isTrue();
            } else {
                assertThat(finalStatus).isEqualTo(FollowRequestStatus.CANCELED);
                assertThat(followExists).as("canceled request implies no follow").isFalse();
            }
            long followers = profileRepository.findById(alice.getId()).orElseThrow().getFollowersCount();
            long following = profileRepository.findById(bob.getId()).orElseThrow().getFollowingCount();
            assertThat(followers).as("counters must mirror the final follow state exactly").isEqualTo(followExists ? 1 : 0);
            assertThat(following).isEqualTo(followExists ? 1 : 0);
        } finally {
            pool.shutdownNow();
        }
    }
}
