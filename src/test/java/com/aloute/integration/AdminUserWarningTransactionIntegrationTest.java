package com.aloute.integration;

import com.aloute.common.exception.ApiException;
import com.aloute.config.DevTestDataInitializer;
import com.aloute.entity.ModerationAction;
import com.aloute.entity.ModerationTargetType;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.ModerationActionRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.service.NotificationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * If the notification transport cannot persist the warning, the audit row must roll
 * back with the transaction; a 200 response would falsely claim the warning was sent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class AdminUserWarningTransactionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private ModerationActionRepository moderationActionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @MockBean private NotificationService notificationService;

    private static final String PASSWORD = "Str0ng@Pass123";
    private Cookie adminCookie;

    @BeforeEach
    void setUp() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(
                                DevTestDataInitializer.ADMIN_EMAIL, DevTestDataInitializer.ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String token = login.getResponse().getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith("aloute_access_token="))
                .findFirst()
                .map(c -> c.substring("aloute_access_token=".length(), c.indexOf(';')))
                .orElseThrow();
        adminCookie = new Cookie("aloute_access_token", token);
    }

    private User createUser() {
        return transactionTemplate.execute(status -> {
            long suffix = System.nanoTime();
            User user = new User();
            user.setUsername("tx_warn_" + suffix);
            user.setEmail("tx_warn_" + suffix + "@aloute.test");
            user.setPasswordHash(passwordEncoder.encode(PASSWORD));
            user.setVerified(true);
            user.setActive(true);
            user.setRole(Role.USER);
            user = userRepository.save(user);
            Profile profile = new Profile();
            profile.setUser(user);
            profile.setDisplayName("Transaction target");
            profileRepository.save(profile);
            return user;
        });
    }

    @Test
    @DisplayName("notification failure rolls back the warning audit and reports an error")
    void notificationFailureRollsBackWarning() throws Exception {
        User target = createUser();
        when(notificationService.createAdministrativeNotification(anyLong(), anyLong(), any(), any(), anyLong(), any()))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "NOTIFICATION_UNAVAILABLE",
                        "Không thể gửi thông báo cảnh báo."));

        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/warn")
                        .cookie(adminCookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"OTHER\",\"reasonDetail\":\"Kiểm thử rollback thông báo cảnh báo.\"}"))
                .andExpect(status().isServiceUnavailable());

        long warningAudits = moderationActionRepository.findByTargetTypeAndTargetId(
                        ModerationTargetType.USER, target.getId(), PageRequest.of(0, 100)).getContent().stream()
                .filter((ModerationAction action) -> "USER_WARN".equals(action.getActionType()))
                .count();
        assertThat(warningAudits).isZero();

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.getRole()).isEqualTo(Role.USER);
        assertThat(reloaded.getAuthVersion()).isZero();
        assertThat(reloaded.getFailedLoginAttempts()).isZero();
    }
}
