package com.aloute.integration;

import com.aloute.dto.request.admin.AdminUserModerationRequest;
import com.aloute.dto.request.admin.AdminUserRoleChangeRequest;
import com.aloute.entity.ModerationTargetType;
import com.aloute.entity.Profile;
import com.aloute.entity.ReportReason;
import com.aloute.entity.User;
import com.aloute.repository.ModerationActionRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.service.UserService;
import com.aloute.service.admin.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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
 * Invariant + concurrency integration tests for the "at least one active SYSTEM_ADMIN"
 * rule, covering both administrative suspension and self-service deactivation.
 *
 * Runs against a real H2 database so the pessimistic lock in {@code SystemAdminGuard}
 * is exercised. The test controls the active-admin universe: pre-existing admins are
 * deactivated for the test body and restored afterwards, so only the admins created by
 * the test are active.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class SystemAdminInvariantIntegrationTest {

    @Autowired private AdminUserService adminUserService;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private ModerationActionRepository moderationActionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;

    private static final String PASSWORD = "Adm1n@Pass123";

    /** id -> previous active flag, for the pre-existing admins we temporarily deactivate. */
    private final List<Object[]> adminSnapshot = new ArrayList<>();
    private final List<Long> createdIds = new ArrayList<>();

    private User adminA;
    private User adminB;

    private AdminUserModerationRequest reason() {
        return new AdminUserModerationRequest(ReportReason.OTHER, "Security invariant regression test reason.");
    }

    private User createUser(String label, Role role) {
        return transactionTemplate.execute(status -> {
            long suffix = System.nanoTime();
            User u = new User();
            u.setUsername("inv_" + label + "_" + suffix);
            u.setEmail("inv_" + label + "_" + suffix + "@aloute.test");
            u.setPasswordHash(passwordEncoder.encode(PASSWORD));
            u.setVerified(true);
            u.setActive(true);
            u.setRole(role);
            u.setAuthVersion(0L);
            u = userRepository.save(u);
            Profile p = new Profile();
            p.setUser(u);
            p.setDisplayName("Inv " + label);
            profileRepository.save(p);
            createdIds.add(u.getId());
            return u;
        });
    }

    private long activeAdminCount() {
        return userRepository.countByRoleAndActive(Role.SYSTEM_ADMIN, true);
    }

    private void setActiveAndVersion(Long id, boolean active, long authVersion) {
        transactionTemplate.execute(status -> {
            User u = userRepository.findById(id).orElseThrow();
            u.setActive(active);
            u.setAuthVersion(authVersion);
            userRepository.save(u);
            return null;
        });
    }

    private void setRoleActiveVersion(Long id, Role role, boolean active, long authVersion) {
        transactionTemplate.execute(status -> {
            User u = userRepository.findById(id).orElseThrow();
            u.setRole(role);
            u.setActive(active);
            u.setAuthVersion(authVersion);
            userRepository.save(u);
            return null;
        });
    }

    private void clearAuditFor(Long userId) {
        transactionTemplate.execute(status -> {
            moderationActionRepository.deleteAll(
                    moderationActionRepository.findByTargetTypeAndTargetId(
                            ModerationTargetType.USER, userId, PageRequest.of(0, 1000)).getContent());
            return null;
        });
    }

    private long suspendAuditCount(Long userId) {
        return moderationActionRepository.findByTargetTypeAndTargetId(
                        ModerationTargetType.USER, userId, PageRequest.of(0, 1000)).getContent().stream()
                .filter(a -> "USER_SUSPEND".equals(a.getActionType()))
                .count();
    }

    private long roleAuditCount(Long userId) {
        return moderationActionRepository.findByTargetTypeAndTargetId(
                        ModerationTargetType.USER, userId, PageRequest.of(0, 1000)).getContent().stream()
                .filter(a -> "USER_ROLE_CHANGE".equals(a.getActionType()))
                .count();
    }

    private AdminUserRoleChangeRequest roleChange(Role newRole) {
        return new AdminUserRoleChangeRequest(newRole, ReportReason.OTHER,
                "Đổi vai trò hệ thống trong regression test concurrency.");
    }

    @BeforeEach
    void isolateAdminUniverse() {
        // Snapshot and deactivate every pre-existing SYSTEM_ADMIN, then create a known pair.
        List<User> existing = transactionTemplate.execute(status ->
                userRepository.lockByRoleOrdered(Role.SYSTEM_ADMIN));
        for (User u : existing) {
            adminSnapshot.add(new Object[]{u.getId(), u.isActive()});
        }
        transactionTemplate.execute(status -> {
            for (Object[] row : adminSnapshot) {
                User u = userRepository.findById((Long) row[0]).orElseThrow();
                u.setActive(false);
                userRepository.save(u);
            }
            return null;
        });
        adminA = createUser("a", Role.SYSTEM_ADMIN);
        adminB = createUser("b", Role.SYSTEM_ADMIN);
    }

    @AfterEach
    void restoreAdminUniverse() {
        transactionTemplate.execute(status -> {
            // Leave the created admins in place (deleting them would break FKs from
            // profiles/audit), but ensure they are inactive so later tests see the
            // original active-admin universe.
            for (Long id : createdIds) {
                userRepository.findById(id).ifPresent(u -> {
                    u.setActive(false);
                    userRepository.save(u);
                });
            }
            for (Object[] row : adminSnapshot) {
                User u = userRepository.findById((Long) row[0]).orElse(null);
                if (u != null) {
                    u.setActive((Boolean) row[1]);
                    userRepository.save(u);
                }
            }
            return null;
        });
        adminSnapshot.clear();
        createdIds.clear();
    }

    // ------------------------------------------------------------------ BUG 1

    @Test
    @DisplayName("Concurrent cross-suspension of two admins can never leave zero active admins")
    void concurrentCrossSuspensionPreservesInvariant() throws Exception {
        for (int iteration = 0; iteration < 25; iteration++) {
            // Fresh pair, both active.
            setActiveAndVersion(adminA.getId(), true, 0L);
            setActiveAndVersion(adminB.getId(), true, 0L);
            clearAuditFor(adminA.getId());
            clearAuditFor(adminB.getId());
            assertThat(activeAdminCount()).isEqualTo(2L);

            List<Object> outcomes = race(
                    () -> adminUserService.suspendUser(adminA.getId(), adminB.getId(), reason()),
                    () -> adminUserService.suspendUser(adminB.getId(), adminA.getId(), reason()));

            long successes = outcomes.stream().filter(o -> "OK".equals(o)).count();
            assertThat(successes)
                    .as("iteration %d: exactly one of the two concurrent suspensions may succeed", iteration)
                    .isEqualTo(1L);
            assertThat(activeAdminCount())
                    .as("iteration %d: at least one active SYSTEM_ADMIN must remain", iteration)
                    .isEqualTo(1L);

            // The rejected request must not have written audit rows or bumped authVersion.
            Long failedTargetId = "OK".equals(outcomes.get(0)) ? adminA.getId() : adminB.getId();
            assertThat(suspendAuditCount(failedTargetId))
                    .as("iteration %d: no audit row for the refused suspension", iteration)
                    .isZero();
        }
    }

    @Test
    @DisplayName("Suspending the sole active admin is refused locally (single-thread) with no side effects")
    void soleActiveAdminSuspensionRefused() {
        // Make B inactive -> A is the sole active admin.
        setActiveAndVersion(adminB.getId(), false, 0L);
        clearAuditFor(adminA.getId());
        long versionBefore = userRepository.findById(adminA.getId()).orElseThrow().getAuthVersion();

        // B (an admin, inactive) suspends A (sole active admin) -> refused.
        assertThatThrownBy(() -> adminUserService.suspendUser(adminB.getId(), adminA.getId(), reason()))
                .isInstanceOf(com.aloute.common.exception.ApiException.class);

        User a = userRepository.findById(adminA.getId()).orElseThrow();
        assertThat(a.isActive()).isTrue();
        assertThat(a.getAuthVersion()).isEqualTo(versionBefore);
        assertThat(suspendAuditCount(adminA.getId())).isZero();
        assertThat(activeAdminCount()).isEqualTo(1L);
    }

    // ------------------------------------------------------------------ BUG 2

    @Test
    @DisplayName("The sole active SYSTEM_ADMIN cannot self-deactivate; a normal user still can")
    void lastActiveAdminSelfDeactivationRefused() {
        setActiveAndVersion(adminB.getId(), false, 0L);
        long versionBefore = userRepository.findById(adminA.getId()).orElseThrow().getAuthVersion();

        assertThatThrownBy(() -> userService.deactivateAccount(adminA.getId(), PASSWORD))
                .isInstanceOf(com.aloute.common.exception.ApiException.class)
                .hasMessageContaining("cuối cùng");

        User a = userRepository.findById(adminA.getId()).orElseThrow();
        assertThat(a.isActive()).isTrue();
        assertThat(a.getAuthVersion()).isEqualTo(versionBefore);
        assertThat(activeAdminCount()).isEqualTo(1L);

        // An ordinary user is unaffected by the admin guard.
        User normal = createUser("normal", Role.USER);
        userService.deactivateAccount(normal.getId(), PASSWORD);
        User reloaded = userRepository.findById(normal.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.getAuthVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("A SYSTEM_ADMIN with an active peer may self-deactivate")
    void adminSelfDeactivationAllowedWithPeer() {
        // Both A and B active.
        userService.deactivateAccount(adminA.getId(), PASSWORD);

        assertThat(userRepository.findById(adminA.getId()).orElseThrow().isActive()).isFalse();
        assertThat(activeAdminCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Concurrent self-deactivation and suspension of the same admin keep one active admin")
    void concurrentSelfDeactivationAndSuspension() throws Exception {
        List<Object> outcomes = race(
                () -> userService.deactivateAccount(adminA.getId(), PASSWORD),
                () -> adminUserService.suspendUser(adminB.getId(), adminA.getId(), reason()));

        long successes = outcomes.stream().filter(o -> "OK".equals(o)).count();
        assertThat(successes).isEqualTo(1L);
        assertThat(activeAdminCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Concurrent mutual demotion can never remove every active SYSTEM_ADMIN")
    void concurrentMutualDemotionPreservesInvariant() throws Exception {
        for (int iteration = 0; iteration < 25; iteration++) {
            Long aId = adminA.getId();
            Long bId = adminB.getId();
            setRoleActiveVersion(aId, Role.SYSTEM_ADMIN, true, 0L);
            setRoleActiveVersion(bId, Role.SYSTEM_ADMIN, true, 0L);
            clearAuditFor(aId);
            clearAuditFor(bId);
            assertThat(activeAdminCount()).isEqualTo(2L);

            List<Object> outcomes = race(
                    () -> adminUserService.changeUserRole(bId, aId, roleChange(Role.USER)),
                    () -> adminUserService.changeUserRole(aId, bId, roleChange(Role.USER)));

            long successes = outcomes.stream().filter(o -> "OK".equals(o)).count();
            assertThat(successes).as("iteration %d: exactly one demotion may win", iteration).isEqualTo(1L);
            assertThat(activeAdminCount()).as("iteration %d: one active admin remains", iteration).isEqualTo(1L);

            Long failedTargetId = "OK".equals(outcomes.get(0)) ? bId : aId;
            assertThat(roleAuditCount(failedTargetId))
                    .as("iteration %d: refused demotion must not write an audit row", iteration)
                    .isZero();
            assertThat(userRepository.findById(failedTargetId).orElseThrow().getAuthVersion())
                    .as("iteration %d: refused demotion must not revoke the target session", iteration)
                    .isZero();
        }
    }

    @Test
    @DisplayName("Concurrent admin demotion and the other admin's self-deactivation keep one active admin")
    void concurrentDemotionAndSelfDeactivationPreservesInvariant() throws Exception {
        Long aId = adminA.getId();
        Long bId = adminB.getId();
        setRoleActiveVersion(aId, Role.SYSTEM_ADMIN, true, 0L);
        setRoleActiveVersion(bId, Role.SYSTEM_ADMIN, true, 0L);
        clearAuditFor(aId);
        clearAuditFor(bId);

        List<Object> outcomes = race(
                () -> adminUserService.changeUserRole(bId, aId, roleChange(Role.USER)),
                () -> userService.deactivateAccount(bId, PASSWORD));

        long successes = outcomes.stream().filter(o -> "OK".equals(o)).count();
        assertThat(successes).isEqualTo(1L);
        assertThat(activeAdminCount()).isEqualTo(1L);
    }

    // ------------------------------------------------------------------ helpers

    /** Runs both runnables as close to simultaneously as possible and returns "OK" or the thrown error. */
    private List<Object> race(Runnable first, Runnable second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> a = guarded(barrier, first);
            Callable<Object> b = guarded(barrier, second);
            Future<Object> fa = pool.submit(a);
            Future<Object> fb = pool.submit(b);
            List<Object> out = new ArrayList<>();
            out.add(fa.get(30, TimeUnit.SECONDS));
            out.add(fb.get(30, TimeUnit.SECONDS));
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Object> guarded(CyclicBarrier barrier, Runnable task) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                task.run();
                return "OK";
            } catch (Throwable t) {
                return t;
            }
        };
    }
}
