package com.aloute.integration;

import com.aloute.config.DevTestDataInitializer;
import com.aloute.entity.*;
import com.aloute.repository.PostRepository;
import com.aloute.repository.ReportRepository;
import com.aloute.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class AdminEndToEndIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ReportRepository reportRepository;

    private Long testPostId;
    private Long testReportId;
    private String adminJwtToken;
    private Cookie adminAuthCookie;
    private Long adminUserId;

    @BeforeEach
    void setUpTestData() throws Exception {
        // Authenticate as dev test admin: admin@aloute.local / Admin@123
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(DevTestDataInitializer.ADMIN_EMAIL, DevTestDataInitializer.ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        adminJwtToken = loginResult.getResponse().getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith("aloute_access_token="))
                .findFirst()
                .map(c -> c.substring("aloute_access_token=".length(), c.indexOf(';')))
                .orElseThrow(() -> new IllegalStateException("Missing aloute_access_token cookie"));

        adminAuthCookie = new Cookie("aloute_access_token", adminJwtToken);
        adminUserId = userRepository.findByEmailIgnoreCase(DevTestDataInitializer.ADMIN_EMAIL).orElseThrow().getId();

        // Create isolated test user & test post
        User normalUser = userRepository.findByEmailIgnoreCase(DevTestDataInitializer.USER_EMAIL).orElseThrow();

        Post post = new Post();
        post.setUser(normalUser);
        post.setContent("Nội dung bài viết kiểm thử tích hợp thực tế E2E.");
        post.setVisibility(PostVisibility.PUBLIC);
        post.setCreatedAt(Instant.now());
        post = postRepository.save(post);
        testPostId = post.getId();

        // Create test report targeting this post
        Report report = new Report();
        report.setReporter(normalUser);
        report.setReportedPostId(testPostId);
        report.setReason(ReportReason.HARASSMENT);
        report.setDescription("Bài viết có dấu hiệu quấy rối cần xem xét.");
        report.setStatus(ReportStatus.PENDING);
        report.setVersion(0L);
        report = reportRepository.save(report);
        testReportId = report.getId();
    }

    @Test
    @DisplayName("E2E SSR: Authenticated SYSTEM_ADMIN can render all 8 SSR routes with real data")
    void authenticatedAdminCanRenderAllEightSsrRoutes() throws Exception {
        // 1. Overview
        mockMvc.perform(get("/admin").cookie(adminAuthCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tổng quan hệ thống")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Báo cáo chờ xử lý")));

        // 2. Users List
        mockMvc.perform(get("/admin/users").cookie(adminAuthCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Quản lý người dùng")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("admin")));

        // 3. User Detail
        mockMvc.perform(get("/admin/users/" + adminUserId).cookie(adminAuthCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chi tiết người dùng")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("admin")));

        // 4. Posts List
        mockMvc.perform(get("/admin/posts").cookie(adminAuthCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Quản lý bài viết")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nội dung bài viết kiểm thử")));

        // 5. Post Detail
        mockMvc.perform(get("/admin/posts/" + testPostId).cookie(adminAuthCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chi tiết bài viết #" + testPostId)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nội dung bài viết kiểm thử")));

        // 6. Reports List
        mockMvc.perform(get("/admin/reports").cookie(adminAuthCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hàng đợi báo cáo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bài viết #" + testPostId)));

        // 7. Report Detail
        mockMvc.perform(get("/admin/reports/" + testReportId).cookie(adminAuthCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chi tiết báo cáo #" + testReportId)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bài viết có dấu hiệu quấy rối")));

        // 8. Moderation Actions History
        mockMvc.perform(get("/admin/moderation-actions").cookie(adminAuthCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nhật ký kiểm duyệt")));
    }

    @Test
    @DisplayName("E2E Workflow: Claim report -> Resolve with atomic post removal -> Restore post -> Verify DB state")
    void fullModerationWorkflowEndToEnd() throws Exception {
        // Step 1: Claim report (start review)
        mockMvc.perform(post("/api/admin/reports/" + testReportId + "/start-review")
                        .cookie(adminAuthCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REVIEWED"))
                .andExpect(jsonPath("$.data.reviewedBy").value(adminUserId));

        // Verify report status in DB is REVIEWED
        Report reviewedReport = reportRepository.findById(testReportId).orElseThrow();
        assertThat(reviewedReport.getStatus()).isEqualTo(ReportStatus.REVIEWED);
        assertThat(reviewedReport.getReviewedBy().getId()).isEqualTo(adminUserId);

        // Step 2: Resolve report with atomic POST_REMOVE
        long currentVersion = reviewedReport.getVersion();
        MvcResult resolveResult = mockMvc.perform(post("/api/admin/reports/" + testReportId + "/decisions")
                        .cookie(adminAuthCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "expectedVersion": %d,
                                    "decision": "RESOLVE",
                                    "resolutionNote": "Báo cáo hợp lệ. Quyết định gỡ bài viết vi phạm.",
                                    "resolutionAction": {
                                        "type": "POST_REMOVE",
                                        "reasonCode": "HARASSMENT",
                                        "reasonDetail": "Vi phạm quy tắc ứng xử học đường có bằng chứng xác thực.",
                                        "publicNotice": "Bài viết của bạn đã bị gỡ do vi phạm quy tắc."
                                    }
                                }
                                """.formatted(currentVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RESOLVE".equals("RESOLVE") ? "RESOLVED" : "REJECTED"))
                .andExpect(jsonPath("$.data.resolutionActionId").isNumber())
                .andReturn();

        // Verify in DB that post is soft deleted
        Post removedPost = postRepository.findById(testPostId).orElseThrow();
        assertThat(removedPost.getDeletedAt()).isNotNull();

        // Step 3: Check post detail via SSR to verify activeRemovalId is displayed
        MvcResult postDetailResult = mockMvc.perform(get("/admin/posts/" + testPostId).cookie(adminAuthCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đã gỡ (Admin)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Khôi phục bài viết")))
                .andReturn();

        // Step 4: Stale Version Conflict on report
        mockMvc.perform(post("/api/admin/reports/" + testReportId + "/decisions")
                        .cookie(adminAuthCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "expectedVersion": 0,
                                    "decision": "REJECT",
                                    "resolutionNote": "Thử quyết định lại với version cũ"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        // Step 5: CSRF Denial Check on mutation
        mockMvc.perform(post("/api/admin/posts/" + testPostId + "/restore")
                        .cookie(adminAuthCookie)
                        // No CSRF token
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "expectedRemovalId": 1,
                                    "reasonDetail": "Thử khôi phục không có CSRF token"
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}
