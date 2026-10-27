package com.aloute.integration;

import com.aloute.dto.request.LoginRequest;
import com.aloute.entity.User;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.service.AuthService;
import com.aloute.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-03: per-account login throttling. Consecutive failed logins lock the
 * account for a cooldown window (429 on any attempt while locked), success
 * resets the counter, and the failure accounting is an atomic UPDATE so
 * concurrent logins cannot lose increments.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
        "aloute.auth.login-max-failed-attempts=5",
        "aloute.auth.login-lockout-minutes=15"
})
class LoginThrottleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthService authService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private User user;
    private static final String PASSWORD = "Correct@Password1";

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        User u = new User();
        u.setUsername("throttle_" + suffix);
        u.setEmail("throttle_" + suffix + "@aloute.test");
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setVerified(true);
        u.setActive(true);
        u.setRole(Role.USER);
        user = userRepository.saveAndFlush(u);
    }

    private void attemptPassword(String password) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + password + "\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
        // JPQL bulk updates bypass the persistence context: evict cached entities
        // so the next request's lockout read sees the freshly updated row.
        entityManager.clear();
    }

    @Test
    @DisplayName("BL-03: 5 consecutive failures lock the account; correct password then returns 429")
    void consecutiveFailuresLockAccount() throws Exception {
        for (int i = 0; i < 5; i++) {
            attemptPassword("wrong-" + i);
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}")
                        .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOO_MANY_ATTEMPTS"));

        User locked = userRepository.findById(user.getId()).orElseThrow();
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("BL-03: successful login resets the failure counter")
    void successfulLoginResetsCounter() throws Exception {
        for (int i = 0; i < 3; i++) {
            attemptPassword("wrong-" + i);
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk());
        entityManager.clear();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getFailedLoginAttempts()).isZero();

        // Four further failures stay below the threshold: no lockout yet.
        for (int i = 0; i < 4; i++) {
            attemptPassword("wrong2-" + i);
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Lockout is time-bound: expired lock allows login again")
    void expiredLockAllowsLoginAgain() throws Exception {
        for (int i = 0; i < 5; i++) {
            attemptPassword("wrong-" + i);
        }
        User locked = userRepository.findById(user.getId()).orElseThrow();
        locked.setLockedUntil(Instant.now().minusSeconds(1));
        userRepository.saveAndFlush(locked);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unknown email never locks and always answers with generic 401")
    void unknownEmailNeverLocks() throws Exception {
        for (int i = 0; i < 7; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody_" + i + "@aloute.test\",\"password\":\"whatever\"}")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
        }
    }

    @Test
    @DisplayName("Concurrent failed logins cannot lose increments: account ends locked exactly once")
    void concurrentFailuresLockAccountAtomically() throws Exception {
        // The test class is @Transactional: REQUIRES_NEW keeps fixture and final
        // assertions outside it so worker threads (own transactions) can see them.
        TransactionTemplate independentTx = new TransactionTemplate(transactionManager);
        independentTx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        String email = independentTx.execute(status -> {
            User u = new User();
            u.setUsername("throttle_conc_" + System.nanoTime());
            u.setEmail("throttle_conc_" + System.nanoTime() + "@aloute.test");
            u.setPasswordHash(passwordEncoder.encode(PASSWORD));
            u.setVerified(true);
            u.setActive(true);
            u.setRole(Role.USER);
            return userRepository.save(u).getEmail();
        });

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger unauthorized = new AtomicInteger();
        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    authService.login(new LoginRequest(email, "totally-wrong"));
                } catch (ApiException ex) {
                    if (ex.getStatus() == HttpStatus.UNAUTHORIZED) {
                        unauthorized.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                }
            }));
        }
        start.countDown();
        for (var future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(unauthorized.get()).isEqualTo(threads);
        User locked = independentTx.execute(status -> userRepository.findByEmailIgnoreCase(email).orElseThrow());
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(threads);
        assertThat(locked.getLockedUntil()).isNotNull();
    }
}
