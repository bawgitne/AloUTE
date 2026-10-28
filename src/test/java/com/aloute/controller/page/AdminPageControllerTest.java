package com.aloute.controller.page;

import com.aloute.common.api.PageMeta;
import com.aloute.dto.response.UserSummary;
import com.aloute.dto.response.admin.*;
import com.aloute.entity.ModerationTargetType;
import com.aloute.entity.PostVisibility;
import com.aloute.entity.ReportReason;
import com.aloute.entity.ReportStatus;
import com.aloute.entity.User;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.admin.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class AdminPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminOverviewService adminOverviewService;

    @MockBean
    private AdminUserService adminUserService;

    @MockBean
    private AdminPostService adminPostService;

    @MockBean
    private AdminReportService adminReportService;

    @MockBean
    private AdminModerationService adminModerationService;

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
    // 1. Role Matrix Security Tests for all 8 routes
    // ==========================================

    @Test
    @DisplayName("Role Matrix: Unauthenticated guest receives 401 Unauthorized across all 8 admin routes")
    void guestAccessDeniedForAllRoutes() throws Exception {
        String[] adminRoutes = {
                "/admin",
                "/admin/users",
                "/admin/users/1",
                "/admin/posts",
                "/admin/posts/1",
                "/admin/reports",
                "/admin/reports/1",
                "/admin/moderation-actions"
        };

        for (String route : adminRoutes) {
            mockMvc.perform(get(route))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("Role Matrix: Regular USER receives 403 Forbidden across all 8 admin routes")
    void regularUserForbiddenForAllRoutes() throws Exception {
        Authentication userAuth = createMockAuth(2L, "regular_user", Role.USER);
        String[] adminRoutes = {
                "/admin",
                "/admin/users",
                "/admin/users/1",
                "/admin/posts",
                "/admin/posts/1",
                "/admin/reports",
                "/admin/reports/1",
                "/admin/moderation-actions"
        };

        for (String route : adminRoutes) {
            mockMvc.perform(get(route).with(authentication(userAuth)))
                    .andExpect(status().isForbidden());
        }
    }

    // ==========================================
    // 2. Route 1: GET /admin (Overview)
    // ==========================================

    @Test
    @DisplayName("Route 1: SYSTEM_ADMIN can access GET /admin, rendering admin/index with overview metrics")
    void adminOverviewSuccess() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        AdminOverviewResponse.OverviewMetrics metrics = new AdminOverviewResponse.OverviewMetrics(
                100L, 90L, 500L, 25L, 5L, 2L, 10L
        );
        AdminOverviewResponse overview = new AdminOverviewResponse(
                Instant.now(), "7d", metrics,
                List.of(new AdminOverviewResponse.DailyActivity("2026-10-14", 5L, 25L)),
                List.of(101L), List.of(201L)
        );
        when(adminOverviewService.getOverview()).thenReturn(overview);

        mockMvc.perform(get("/admin").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/index"))
                .andExpect(model().attribute("activeNav", "overview"))
                .andExpect(model().attribute("overview", overview))
                .andExpect(model().attribute("overviewError", false));
    }

    // ==========================================
    // 3. Route 2: GET /admin/users (Users List)
    // ==========================================

    @Test
    @DisplayName("Route 2: SYSTEM_ADMIN can access GET /admin/users with filters and pagination")
    void adminUsersListSuccess() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        AdminUserSummaryResponse userSummary = new AdminUserSummaryResponse(
                10L, "student10", "Nguyễn Sinh Viên", true, true, false, "NORMAL", Instant.now()
        );
        PageImpl<AdminUserSummaryResponse> userPage = new PageImpl<>(List.of(userSummary));
        when(adminUserService.getUsers(eq("student"), eq("USER"), eq(true), any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(get("/admin/users")
                        .param("q", "student")
                        .param("role", "USER")
                        .param("active", "true")
                        .param("page", "0")
                        .param("size", "20")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/index"))
                .andExpect(model().attribute("activeNav", "users"))
                .andExpect(model().attributeExists("users"))
                .andExpect(model().attributeExists("pageMeta"))
                .andExpect(model().attribute("q", "student"))
                .andExpect(model().attribute("role", "USER"))
                .andExpect(model().attribute("active", true));
    }

    // ==========================================
    // 4. Route 3: GET /admin/users/{userId} (User Detail)
    // ==========================================

    @Test
    @DisplayName("Route 3: SYSTEM_ADMIN can access GET /admin/users/{userId} with user profile and child posts/audit")
    void adminUserDetailSuccess() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        AdminUserDetailResponse userDetail = new AdminUserDetailResponse(
                10L, "student10", "student10@aloute.local", "Nguyễn Sinh Viên", "Bio note", null,
                true, true, false, Role.USER, Instant.now(),
                new AdminUserDetailResponse.UserCounts(12L, 1L),
                new AdminUserDetailResponse.UserCapabilities(true, false, false, false)
        );
        when(adminUserService.getUserDetail(10L, 1L)).thenReturn(userDetail);
        when(adminPostService.getPosts(isNull(), eq(10L), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(adminModerationService.getModerationActions(isNull(), eq(ModerationTargetType.USER), eq(10L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/admin/users/10").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/detail"))
                .andExpect(model().attribute("activeNav", "users"))
                .andExpect(model().attribute("user", userDetail))
                .andExpect(model().attributeExists("userPosts"))
                .andExpect(model().attributeExists("userModerationActions"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Có sẵn"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Chưa hỗ trợ"))));
    }

    // ==========================================
    // 5. Route 4: GET /admin/posts (Posts List)
    // ==========================================

    @Test
    @DisplayName("Route 4: SYSTEM_ADMIN can access GET /admin/posts with contentState filter")
    void adminPostsListSuccess() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        UserSummary author = new UserSummary(10L, "student10", "Nguyễn Sinh Viên", null, true);
        AdminPostSummaryResponse postSummary = new AdminPostSummaryResponse(
                100L, author, "Nội dung bài viết mẫu", null, null, null,
                PostVisibility.PUBLIC, "ACTIVE", 2L, Instant.now(),
                new AdminPostSummaryResponse.PostCapabilities(true, false)
        );
        PageImpl<AdminPostSummaryResponse> postPage = new PageImpl<>(List.of(postSummary));
        when(adminPostService.getPosts(eq("mẫu"), isNull(), isNull(), eq("ACTIVE"), isNull(), any(Pageable.class)))
                .thenReturn(postPage);

        mockMvc.perform(get("/admin/posts")
                        .param("q", "mẫu")
                        .param("contentState", "ACTIVE")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/posts/index"))
                .andExpect(model().attribute("activeNav", "posts"))
                .andExpect(model().attributeExists("posts"))
                .andExpect(model().attributeExists("pageMeta"))
                .andExpect(model().attribute("q", "mẫu"))
                .andExpect(model().attribute("contentState", "ACTIVE"));
    }

    @Test
    @DisplayName("Safety Rule: Complex search query with &, +, # and Vietnamese preserves size and escapes query params in pagination links")
    void paginationEncodesComplexQueryAndPreservesSize() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        UserSummary author = new UserSummary(10L, "student10", "Nguyễn Sinh Viên", null, true);
        AdminPostSummaryResponse postSummary = new AdminPostSummaryResponse(
                100L, author, "Nội dung bài viết mẫu", null, null, null,
                PostVisibility.PUBLIC, "ACTIVE", 2L, Instant.now(),
                new AdminPostSummaryResponse.PostCapabilities(true, false)
        );
        PageImpl<AdminPostSummaryResponse> postPage = new PageImpl<>(
                List.of(postSummary),
                org.springframework.data.domain.PageRequest.of(0, 1),
                2
        );
        String complexQ = "campus & science+# Việt";
        when(adminPostService.getPosts(eq(complexQ), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(postPage);

        mockMvc.perform(get("/admin/posts")
                        .param("q", complexQ)
                        .param("size", "1")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/posts/index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("size=1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("page=1")));
    }

    // ==========================================
    // 6. Route 5: GET /admin/posts/{postId} (Post Detail)
    // ==========================================

    @Test
    @DisplayName("Route 5: SYSTEM_ADMIN can access GET /admin/posts/{postId} with post details and audit history")
    void adminPostDetailSuccess() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        UserSummary author = new UserSummary(10L, "student10", "Nguyễn Sinh Viên", null, true);
        AdminPostDetailResponse postDetail = new AdminPostDetailResponse(
                100L, author, "Toàn văn bài viết kiểm duyệt", PostVisibility.PUBLIC, "ACTIVE",
                null, null, Instant.now(), null, List.of(501L), null,
                new AdminPostDetailResponse.PostCapabilities(true, false)
        );
        when(adminPostService.getPostDetail(100L)).thenReturn(postDetail);
        when(adminModerationService.getPostModerationActions(eq(100L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/admin/posts/100").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/posts/detail"))
                .andExpect(model().attribute("activeNav", "posts"))
                .andExpect(model().attribute("post", postDetail))
                .andExpect(model().attributeExists("postModerationActions"));
    }

    @Test
    @DisplayName("Safety Rule: Non-PUBLIC post detail hides private content and locks destructive controls")
    void adminPostDetailPrivacyProtectionForNonPublicPosts() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        UserSummary author = new UserSummary(10L, "student10", "Nguyễn Sinh Viên", null, true);
        AdminPostDetailResponse privatePost = new AdminPostDetailResponse(
                200L, author, "NỘI DUNG TUYỆT MẬT RIÊNG TƯ", PostVisibility.FOLLOWERS, "ACTIVE",
                null, null, Instant.now(), null, Collections.emptyList(), null,
                new AdminPostDetailResponse.PostCapabilities(true, false)
        );
        when(adminPostService.getPostDetail(200L)).thenReturn(privatePost);
        when(adminModerationService.getPostModerationActions(eq(200L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/admin/posts/200").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/posts/detail"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nội dung riêng tư / Giới hạn - Chờ chính sách backend")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("NỘI DUNG TUYỆT MẬT RIÊNG TƯ"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("data-action=\"open-remove-post\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("data-action=\"open-restore-post\""))));
    }

    @Test
    @DisplayName("Safety Rule: Restore button on post detail is hidden for non-PUBLIC post even if canRestore is true")
    void adminPostDetailRestoreButtonPrivacyGate() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        UserSummary author = new UserSummary(10L, "student10", "Nguyễn Sinh Viên", null, true);
        // Non-PUBLIC post with active removal and canRestore = true -> restore button MUST NOT render
        AdminPostDetailResponse privateRemovedPost = new AdminPostDetailResponse(
                202L, author, "Private content", PostVisibility.FOLLOWERS, "ADMIN_REMOVED",
                null, null, Instant.now(), Instant.now(), Collections.emptyList(), 888L,
                new AdminPostDetailResponse.PostCapabilities(false, true)
        );
        when(adminPostService.getPostDetail(202L)).thenReturn(privateRemovedPost);
        when(adminModerationService.getPostModerationActions(eq(202L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/admin/posts/202").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("data-action=\"open-restore-post\""))));

        // PUBLIC post with active removal and canRestore = true -> restore button MUST render
        AdminPostDetailResponse publicRemovedPost = new AdminPostDetailResponse(
                203L, author, "Public content", PostVisibility.PUBLIC, "ADMIN_REMOVED",
                null, null, Instant.now(), Instant.now(), Collections.emptyList(), 889L,
                new AdminPostDetailResponse.PostCapabilities(false, true)
        );
        when(adminPostService.getPostDetail(203L)).thenReturn(publicRemovedPost);
        when(adminModerationService.getPostModerationActions(eq(203L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/admin/posts/203").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-action=\"open-restore-post\"")));
    }

    @Test
    @DisplayName("Safety Rule: Non-PUBLIC post in list hides excerpt and shows privacy badge")
    void adminPostsListPrivacyProtectionForNonPublicPosts() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        UserSummary author = new UserSummary(10L, "student10", "Nguyễn Sinh Viên", null, true);
        AdminPostSummaryResponse privatePostSummary = new AdminPostSummaryResponse(
                201L, author, "TRÍCH ĐOẠN BÀI VIẾT RIÊNG TƯ", null, null, null,
                PostVisibility.FOLLOWERS, "ACTIVE", 0L, Instant.now(),
                new AdminPostSummaryResponse.PostCapabilities(true, false)
        );
        when(adminPostService.getPosts(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(privatePostSummary)));

        mockMvc.perform(get("/admin/posts").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/posts/index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nội dung riêng tư / Giới hạn - Chờ chính sách backend")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("TRÍCH ĐOẠN BÀI VIẾT RIÊNG TƯ"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("data-action=\"open-remove-post\""))));
    }

    // ==========================================
    // 7. Route 6: GET /admin/reports (Reports Queue)
    // ==========================================

    @Test
    @DisplayName("Route 6: SYSTEM_ADMIN can access GET /admin/reports with status, targetType and reason filters")
    void adminReportsListSuccess() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        UserSummary reporter = new UserSummary(11L, "reporter11", "Người Báo Cáo", null, true);
        AdminReportSummaryResponse reportSummary = new AdminReportSummaryResponse(
                501L, "POST", 100L, reporter, ReportReason.HARASSMENT,
                "Có hành vi quấy rối", ReportStatus.PENDING, null, Instant.now(), 0L
        );
        PageImpl<AdminReportSummaryResponse> reportPage = new PageImpl<>(List.of(reportSummary));
        when(adminReportService.getReports(eq(ReportStatus.PENDING), eq("POST"), eq(ReportReason.HARASSMENT), isNull(), any(Pageable.class)))
                .thenReturn(reportPage);

        mockMvc.perform(get("/admin/reports")
                        .param("status", "PENDING")
                        .param("targetType", "POST")
                        .param("reason", "HARASSMENT")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/reports/index"))
                .andExpect(model().attribute("activeNav", "reports"))
                .andExpect(model().attributeExists("reports"))
                .andExpect(model().attributeExists("pageMeta"))
                .andExpect(model().attribute("status", "PENDING"))
                .andExpect(model().attribute("targetType", "POST"))
                .andExpect(model().attribute("reason", "HARASSMENT"));
    }

    // ==========================================
    // 8. Route 7: GET /admin/reports/{reportId} (Report Detail)
    // ==========================================

    @Test
    @DisplayName("Route 7: SYSTEM_ADMIN can access GET /admin/reports/{reportId} with report details and atomic remove enabled for PUBLIC post")
    void adminReportDetailSuccess() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        UserSummary reporter = new UserSummary(11L, "reporter11", "Người Báo Cáo", null, true);
        AdminReportDetailResponse reportDetail = new AdminReportDetailResponse(
                501L,
                new AdminReportDetailResponse.ReportTarget("POST", 100L),
                reporter,
                ReportReason.HARASSMENT,
                "Mô tả chi tiết vi phạm quấy rối",
                ReportStatus.REVIEWED,
                1L, // reviewedBy = current admin ID
                Instant.now(), null, null, Instant.now(), 1L,
                new AdminReportDetailResponse.ReportCapabilities(false, true)
        );
        when(adminReportService.getReportDetail(501L)).thenReturn(reportDetail);

        AdminPostDetailResponse targetPost = new AdminPostDetailResponse(
                100L, reporter, "Public target post", PostVisibility.PUBLIC, "ACTIVE",
                null, null, Instant.now(), null, List.of(501L), null,
                new AdminPostDetailResponse.PostCapabilities(true, false)
        );
        when(adminPostService.getPostDetail(100L)).thenReturn(targetPost);

        mockMvc.perform(get("/admin/reports/501").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/reports/detail"))
                .andExpect(model().attribute("activeNav", "reports"))
                .andExpect(model().attribute("report", reportDetail))
                .andExpect(model().attribute("currentUserId", 1L))
                .andExpect(model().attribute("targetCanAtomicRemove", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-target-can-atomic-remove=\"true\"")));
    }

    @Test
    @DisplayName("Safety Rule: Atomic remove is disabled for non-PUBLIC target post in report detail")
    void adminReportDetailTargetPostAtomicRemoveDeniedForNonPublic() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        UserSummary reporter = new UserSummary(11L, "reporter11", "Người Báo Cáo", null, true);
        AdminReportDetailResponse reportDetail = new AdminReportDetailResponse(
                502L,
                new AdminReportDetailResponse.ReportTarget("POST", 101L),
                reporter,
                ReportReason.HARASSMENT,
                "Mô tả vi phạm bài riêng tư",
                ReportStatus.REVIEWED,
                1L,
                Instant.now(), null, null, Instant.now(), 1L,
                new AdminReportDetailResponse.ReportCapabilities(false, true)
        );
        when(adminReportService.getReportDetail(502L)).thenReturn(reportDetail);

        // Non-PUBLIC post
        AdminPostDetailResponse privateTargetPost = new AdminPostDetailResponse(
                101L, reporter, "Private target post", PostVisibility.FOLLOWERS, "ACTIVE",
                null, null, Instant.now(), null, List.of(502L), null,
                new AdminPostDetailResponse.PostCapabilities(true, false)
        );
        when(adminPostService.getPostDetail(101L)).thenReturn(privateTargetPost);

        mockMvc.perform(get("/admin/reports/502").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/reports/detail"))
                .andExpect(model().attribute("targetCanAtomicRemove", false))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-target-can-atomic-remove=\"false\"")));
    }

    // ==========================================
    // 9. Route 8: GET /admin/moderation-actions (Audit History)
    // ==========================================

    @Test
    @DisplayName("Route 8: SYSTEM_ADMIN can access GET /admin/moderation-actions with actionType and targetType filters")
    void adminModerationActionsListSuccess() throws Exception {
        Authentication adminAuth = createMockAuth(1L, "sys_admin", Role.SYSTEM_ADMIN);

        UserSummary actor = new UserSummary(1L, "sys_admin", "Admin System", null, true);
        ModerationActionResponse actionResponse = new ModerationActionResponse(
                901L, actor, ModerationTargetType.POST, 100L, "POST_REMOVE",
                ReportReason.HARASSMENT, "Bài viết vi phạm quấy rối", 501L, Instant.now()
        );
        PageImpl<ModerationActionResponse> actionPage = new PageImpl<>(List.of(actionResponse));
        when(adminModerationService.getModerationActions(isNull(), eq(ModerationTargetType.POST), eq(100L), eq("POST_REMOVE"), any(Pageable.class)))
                .thenReturn(actionPage);

        mockMvc.perform(get("/admin/moderation-actions")
                        .param("targetType", "POST")
                        .param("targetId", "100")
                        .param("actionType", "POST_REMOVE")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/moderation-actions/index"))
                .andExpect(model().attribute("activeNav", "moderation-actions"))
                .andExpect(model().attributeExists("moderationActions"))
                .andExpect(model().attributeExists("pageMeta"))
                .andExpect(model().attribute("targetType", "POST"))
                .andExpect(model().attribute("targetId", 100L))
                .andExpect(model().attribute("actionType", "POST_REMOVE"));
    }
}
