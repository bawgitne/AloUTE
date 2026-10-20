package com.aloute.integration;

import com.aloute.security.authorization.Role;
import com.aloute.entity.User;
import com.aloute.repository.UserRepository;
import com.aloute.security.jwt.JwtSessionService;
import com.aloute.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class UserAuthVersionDurabilityTest {

    @Autowired private UserRepository userRepository;
    @Autowired private com.aloute.repository.ProfileRepository profileRepository;
    @Autowired private com.aloute.service.UserService userService;
    @Autowired private com.aloute.service.PasswordResetService passwordResetService;
    @Autowired private com.aloute.service.email.TestEmailSink emailSink;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JwtSessionService jwtSessionService;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void staleUserWriteCannotRestoreOldPasswordOrLowerAuthVersion() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long suffix = System.currentTimeMillis();

        // 1. Create a user at authVersion = 0, version = 0
        Long userId = tx.execute(status -> {
            User user = new User();
            user.setUsername("durability_" + suffix);
            user.setEmail("durability_" + suffix + "@example.com");
            user.setPasswordHash(passwordEncoder.encode("oldPassword123"));
            user.setRole(Role.USER);
            user.setActive(true);
            user.setAuthVersion(0L);
            return userRepository.saveAndFlush(user).getId();
        });

        // Issue valid JWT for initial state (authVersion = 0)
        User initialUser = userRepository.findById(userId).orElseThrow();
        String oldToken = jwtTokenProvider.generate(com.aloute.security.principal.UserPrincipal.from(initialUser));
        assertThat(jwtSessionService.authenticate(oldToken)).isNotNull();

        // 2. T1: Read user into a detached/stale state
        User staleUser = userRepository.findById(userId).orElseThrow();
        assertThat(staleUser.getVersion()).isEqualTo(0L);

        // 3. T2: Complete a password reset transaction that increments authVersion and entity version
        tx.execute(status -> {
            User inT2 = userRepository.findByIdForUpdate(userId).orElseThrow();
            inT2.setPasswordHash(passwordEncoder.encode("brandNewPassword456"));
            inT2.setAuthVersion(inT2.getAuthVersion() + 1L);
            userRepository.saveAndFlush(inT2);
            return null;
        });

        // Verify T2 committed
        User afterT2 = userRepository.findById(userId).orElseThrow();
        assertThat(afterT2.getAuthVersion()).isEqualTo(1L);
        assertThat(afterT2.getVersion()).isEqualTo(1L);

        // 4. T1: Attempt stale user update (e.g. updating profile privateAccount)
        staleUser.setPrivateAccount(true);

        // MUST fail with OptimisticLockingFailureException due to @Version conflict
        assertThatThrownBy(() -> {
            tx.execute(status -> {
                userRepository.saveAndFlush(staleUser);
                return null;
            });
        }).isInstanceOf(OptimisticLockingFailureException.class);

        // 5. Verify durable guarantees:
        User finalUser = userRepository.findById(userId).orElseThrow();

        // AuthVersion must NEVER decrease
        assertThat(finalUser.getAuthVersion()).isEqualTo(1L);

        // Old password hash must NEVER be restored
        assertThat(passwordEncoder.matches("oldPassword123", finalUser.getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches("brandNewPassword456", finalUser.getPasswordHash())).isTrue();

        // Old JWT token must remain REJECTED
        assertThatThrownBy(() -> jwtSessionService.authenticate(oldToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Session revoked or expired due to password reset");
    }

    @Test
    void stalePersistenceContextRejectsOldJwtWhenAuthVersionChangedInAnotherTransaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long suffix = System.currentTimeMillis();

        // 1. Create a user at authVersion = 0
        Long userId = tx.execute(status -> {
            User user = new User();
            user.setUsername("stale_l1_" + suffix);
            user.setEmail("stale_l1_" + suffix + "@example.com");
            user.setPasswordHash(passwordEncoder.encode("oldPass123"));
            user.setRole(Role.USER);
            user.setActive(true);
            user.setAuthVersion(0L);
            return userRepository.saveAndFlush(user).getId();
        });

        User initialUser = userRepository.findById(userId).orElseThrow();
        String oldToken = jwtTokenProvider.generate(com.aloute.security.principal.UserPrincipal.from(initialUser));
        assertThat(jwtSessionService.authenticate(oldToken)).isNotNull();

        // 2. Open transaction T1 and load the user into T1's persistence context (L1 cache)
        tx.execute(statusT1 -> {
            User managedUserInT1 = userRepository.findById(userId).orElseThrow();
            assertThat(managedUserInT1.getAuthVersion()).isEqualTo(0L);

            // 3. Concurrently in T2: perform password reset (authVersion = 1) and commit
            TransactionTemplate tx2 = new TransactionTemplate(transactionManager);
            tx2.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx2.execute(statusT2 -> {
                User inT2 = userRepository.findByIdForUpdate(userId).orElseThrow();
                inT2.setAuthVersion(inT2.getAuthVersion() + 1L);
                userRepository.saveAndFlush(inT2);
                return null;
            });

            // Even though T1's EntityManager still holds the cached managed User with authVersion = 0:
            assertThat(managedUserInT1.getAuthVersion()).isEqualTo(0L);

            // 4. Calling canonical jwtSessionService.authenticate(oldToken) MUST reject
            // based on the durable version from the database, NOT accept the cached managed User!
            assertThatThrownBy(() -> jwtSessionService.authenticate(oldToken))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("Session revoked or expired due to password reset");

            return null;
        });
    }

    @Test
    void concurrentResetVersusProfileUpdateRegression() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long suffix = System.currentTimeMillis();

        // 1. Create a user and profile at authVersion = 0, version = 0
        Long userId = tx.execute(status -> {
            User user = new User();
            user.setUsername("dur_prof_" + suffix);
            user.setEmail("dur_prof_" + suffix + "@example.com");
            user.setPasswordHash(passwordEncoder.encode("oldPassword123"));
            user.setRole(Role.USER);
            user.setActive(true);
            user.setPrivateAccount(false);
            user.setAuthVersion(0L);
            user = userRepository.saveAndFlush(user);

            com.aloute.entity.Profile profile = new com.aloute.entity.Profile();
            profile.setUser(user);
            profile.setDisplayName("User " + suffix);
            profileRepository.saveAndFlush(profile);

            return user.getId();
        });

        // Generate valid JWT token for authVersion = 0
        User initialUser = userRepository.findById(userId).orElseThrow();
        String initialToken = jwtTokenProvider.generate(com.aloute.security.principal.UserPrincipal.from(initialUser));
        assertThat(jwtSessionService.authenticate(initialToken)).isNotNull();
        assertThat(initialUser.getVersion()).isEqualTo(0L);
        assertThat(initialUser.getAuthVersion()).isEqualTo(0L);

        // 2. Normal profile update: updates profile/privacy and increments @Version version,
        // BUT MUST NOT touch authVersion (sessions remain valid). Proves authVersion is NOT JPA row version.
        com.aloute.dto.request.UpdateProfileRequest updateReq = new com.aloute.dto.request.UpdateProfileRequest();
        updateReq.setPrivateAccount(true);
        userService.updateCurrentProfile(userId, updateReq);

        User afterNormalUpdate = userRepository.findById(userId).orElseThrow();
        assertThat(afterNormalUpdate.isPrivateAccount()).isTrue();
        assertThat(afterNormalUpdate.getVersion()).isEqualTo(1L); // JPA @Version incremented to 1
        assertThat(afterNormalUpdate.getAuthVersion()).isEqualTo(0L); // authVersion remains 0!
        assertThat(jwtSessionService.authenticate(initialToken)).isNotNull(); // Login session remains active!

        // 3. Initiate password reset to obtain a valid reset token
        emailSink.clearSink();
        passwordResetService.initiateForgotPassword(new com.aloute.dto.request.ForgotPasswordRequest(afterNormalUpdate.getEmail()));
        assertThat(emailSink.awaitEmailCount(1, 2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        String rawToken = emailSink.getEmailSink().get(0).resetUrl().split("#token=")[1];

        // 4. Concurrency scenario:
        // T1 starts, loads User (managed in T1's persistence context with version = 1L)
        // T2 concurrently commits password reset (updates passwordHash, increments authVersion to 1L, version to 2L)
        // T1 attempts to write stale privacy setting and flush
        // MUST fail with OptimisticLockingFailureException!
        assertThatThrownBy(() -> {
            tx.execute(statusT1 -> {
                User managedInT1 = userRepository.findById(userId).orElseThrow();
                assertThat(managedInT1.getVersion()).isEqualTo(1L);
                assertThat(managedInT1.getAuthVersion()).isEqualTo(0L);

                // T2 runs concurrently in a separate transaction and commits
                TransactionTemplate tx2 = new TransactionTemplate(transactionManager);
                tx2.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tx2.execute(statusT2 -> {
                    passwordResetService.resetPassword(new com.aloute.dto.request.ResetPasswordRequest(
                            rawToken, "brandNewPassword456", "brandNewPassword456"));
                    return null;
                });

                // T1 now attempts to modify and flush stale managed User
                managedInT1.setPrivateAccount(false);
                userRepository.saveAndFlush(managedInT1);
                return null;
            });
        }).isInstanceOf(OptimisticLockingFailureException.class);

        // 5. Verify invariant guarantees:
        User finalUser = userRepository.findById(userId).orElseThrow();

        // Invariant A: authVersion must NEVER decrease (retains 1L from reset)
        assertThat(finalUser.getAuthVersion()).isEqualTo(1L);

        // Invariant B: passwordHash must NOT be restored to old password; remains new password
        assertThat(passwordEncoder.matches("oldPassword123", finalUser.getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches("brandNewPassword456", finalUser.getPasswordHash())).isTrue();

        // Invariant C: Initial JWT token (issued at authVersion = 0) must remain rejected
        assertThatThrownBy(() -> jwtSessionService.authenticate(initialToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Session revoked or expired due to password reset");

        // Invariant D: New JWT token minted with finalUser (authVersion = 1) is accepted
        String newToken = jwtTokenProvider.generate(com.aloute.security.principal.UserPrincipal.from(finalUser));
        assertThat(jwtSessionService.authenticate(newToken)).isNotNull();
    }
}
