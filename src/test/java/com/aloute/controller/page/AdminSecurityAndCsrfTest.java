package com.aloute.controller.page;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.admin.AdminRemovePostRequest;
import com.aloute.dto.request.admin.AdminReportDecisionRequest;
import com.aloute.dto.request.admin.AdminRestorePostRequest;
import com.aloute.dto.request.admin.AdminStartReportReviewRequest;
import com.aloute.dto.response.admin.PostRemovalActionResponse;
import com.aloute.dto.response.admin.PostRestoreActionResponse;
import com.aloute.dto.response.admin.ReportDecisionResponse;
import com.aloute.dto.response.admin.ReportReviewResponse;
import com.aloute.entity.ReportReason;
import com.aloute.entity.User;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.admin.AdminModerationService;
import com.aloute.service.admin.AdminReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class AdminSecurityAndCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminModerationService adminModerationService;

    @MockBean
    private AdminReportService adminReportService;

    private Authentication createMockAuth(Long userId, String username, Role role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", userId);
        user.setUsername(username);
        user.setEmail(username + "@aloute.local");
        user.setPasswordHash("hash1234");
        user.setActive(true);
        user.setRole(role);
        UserPrincipal principal = UserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    // ==========================================
    // 1. CSRF Protection on Admin Mutations
    // ==========================================

    @Test
    @DisplayName("CSRF Denial: POST /api/admin/posts/{id}/remove without CSRF token is rejected with 403")
    void removePostWithoutCsrfDenied() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        AdminRemovePostRequest request = new AdminRemovePostRequest(
                ReportReason.SPAM, "Chi tiết lý do từ 10 đến 1000 ký tự hợp lệ", null, null
        );

        mockMvc.perform(post("/api/admin/posts/100/remove")
                        .with(authentication(adminAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CSRF Denial: POST /api/admin/reports/{id}/decisions without CSRF token is rejected with 403")
    void decideReportWithoutCsrfDenied() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        AdminReportDecisionRequest request = new AdminReportDecisionRequest(
                0L, "REJECT", "Ghi chú xử lý từ 10 đến 1000 ký tự hợp lệ", null
        );

        mockMvc.perform(post("/api/admin/reports/501/decisions")
                        .with(authentication(adminAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 2. CSRF Allowed + RBAC Enforcement
    // ==========================================

    @Test
    @DisplayName("RBAC: Regular USER with CSRF is denied 403 Forbidden for admin mutation")
    void regularUserWithCsrfForbidden() throws Exception {
        Authentication userAuth = createMockAuth(2L, "regular_user", Role.USER);

        AdminRemovePostRequest request = new AdminRemovePostRequest(
                ReportReason.SPAM, "Chi tiết lý do từ 10 đến 1000 ký tự hợp lệ", null, null
        );

        mockMvc.perform(post("/api/admin/posts/100/remove")
                        .with(authentication(userAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Success: SYSTEM_ADMIN with valid CSRF token can execute post removal")
    void adminRemovePostWithCsrfSuccess() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        AdminRemovePostRequest request = new AdminRemovePostRequest(
                ReportReason.HARASSMENT, "Bài viết quấy rối cá nhân có bằng chứng", null, "Bài viết vi phạm"
        );
        PostRemovalActionResponse response = new PostRemovalActionResponse(
                901L, 100L, "POST_REMOVE", "ADMIN_REMOVED", 701L, Instant.now()
        );
        when(adminModerationService.removePost(eq(1L), eq(100L), any(AdminRemovePostRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/admin/posts/100/remove")
                        .with(authentication(adminAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.actionId").value(901))
                .andExpect(jsonPath("$.data.contentState").value("ADMIN_REMOVED"));
    }

    @Test
    @DisplayName("Success: SYSTEM_ADMIN with valid CSRF token can execute post restore")
    void adminRestorePostWithCsrfSuccess() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        AdminRestorePostRequest request = new AdminRestorePostRequest(
                701L, "Khôi phục sau khi xem xét lại bằng chứng xác đáng"
        );
        PostRestoreActionResponse response = new PostRestoreActionResponse(
                902L, 100L, "POST_RESTORE", "ACTIVE", 701L, Instant.now()
        );
        when(adminModerationService.restorePost(eq(1L), eq(100L), any(AdminRestorePostRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/admin/posts/100/restore")
                        .with(authentication(adminAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.actionId").value(902))
                .andExpect(jsonPath("$.data.contentState").value("ACTIVE"));
    }

    @Test
    @DisplayName("Success: SYSTEM_ADMIN with valid CSRF token can claim/start report review")
    void adminStartReportReviewWithCsrfSuccess() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        AdminStartReportReviewRequest request = new AdminStartReportReviewRequest(0L);
        ReportReviewResponse response = new ReportReviewResponse(
                501L, "REVIEWED", 1L, Instant.now(), 1L
        );
        when(adminReportService.startReview(eq(1L), eq(501L), any(AdminStartReportReviewRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/admin/reports/501/start-review")
                        .with(authentication(adminAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REVIEWED"))
                .andExpect(jsonPath("$.data.reviewedBy").value(1));
    }

    // ==========================================
    // 3. Concurrency & Version Conflict Handling
    // ==========================================

    @Test
    @DisplayName("Conflict: Stale report expectedVersion throws 409 Conflict with REPORT_VERSION_CONFLICT error code")
    void reportVersionConflictReturns409() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        AdminStartReportReviewRequest request = new AdminStartReportReviewRequest(0L);
        when(adminReportService.startReview(eq(1L), eq(501L), any(AdminStartReportReviewRequest.class)))
                .thenThrow(new ApiException(ErrorCode.REPORT_VERSION_CONFLICT, "Report data was modified concurrently"));

        mockMvc.perform(post("/api/admin/reports/501/start-review")
                        .with(authentication(adminAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REPORT_VERSION_CONFLICT.getCode()))
                .andExpect(jsonPath("$.error.message").value("Report data was modified concurrently"));
    }
}
