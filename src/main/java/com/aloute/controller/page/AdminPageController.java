package com.aloute.controller.page;

import com.aloute.common.api.PageMeta;
import com.aloute.dto.response.admin.*;
import com.aloute.entity.ModerationTargetType;
import com.aloute.entity.PostVisibility;
import com.aloute.entity.ReportReason;
import com.aloute.entity.ReportStatus;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.admin.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AdminPageController {

    private final AdminOverviewService adminOverviewService;
    private final AdminUserService adminUserService;
    private final AdminPostService adminPostService;
    private final AdminReportService adminReportService;
    private final AdminModerationService adminModerationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping({"", "/"})
    public String overview(Model model) {
        populateCommonAttributes(model, "overview", "Tổng quan quản trị");
        try {
            AdminOverviewResponse overview = adminOverviewService.getOverview();
            model.addAttribute("overview", overview);
            model.addAttribute("overviewError", false);
        } catch (Exception ex) {
            log.error("Failed to load admin overview", ex);
            model.addAttribute("overview", null);
            model.addAttribute("overviewError", true);
        }
        return "admin/index";
    }

    @GetMapping("/users")
    public String users(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            Model model
    ) {
        populateCommonAttributes(model, "users", "Quản lý người dùng");
        try {
            Pageable pageable = AdminSortHelper.createPageable(page, size, sort, AdminSortHelper.USER_SORT_FIELDS, "createdAt");
            Page<AdminUserSummaryResponse> userPage = adminUserService.getUsers(q, role, active, pageable);
            model.addAttribute("users", userPage.getContent());
            model.addAttribute("pageMeta", PageMeta.from(userPage));
        } catch (Exception ex) {
            log.error("Failed to load users list", ex);
            model.addAttribute("users", null);
            model.addAttribute("pageMeta", null);
            model.addAttribute("errorMessage", "Không thể tải danh sách người dùng.");
        }
        model.addAttribute("q", q != null ? q : "");
        model.addAttribute("role", role != null ? role : "");
        model.addAttribute("active", active);
        model.addAttribute("sort", sort);
        model.addAttribute("size", size);
        return "admin/users/index";
    }

    @GetMapping("/users/{userId}")
    public String userDetail(@PathVariable Long userId, Model model) {
        populateCommonAttributes(model, "users", "Chi tiết người dùng");
        try {
            Long viewerId = currentUserProvider.getCurrentUserId().orElse(null);
            AdminUserDetailResponse user = adminUserService.getUserDetail(userId, viewerId);
            model.addAttribute("user", user);

            try {
                Page<AdminPostSummaryResponse> userPosts = adminPostService.getPosts(
                        null, userId, null, null, null,
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
                );
                model.addAttribute("userPosts", userPosts.getContent());
            } catch (Exception ex) {
                log.warn("Failed to load posts for user {}", userId, ex);
                model.addAttribute("userPosts", java.util.Collections.emptyList());
            }

            try {
                Page<ModerationActionResponse> userActions = adminModerationService.getModerationActions(
                        null, ModerationTargetType.USER, userId, null,
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
                );
                model.addAttribute("userModerationActions", userActions.getContent());
            } catch (Exception ex) {
                log.warn("Failed to load moderation actions for user {}", userId, ex);
                model.addAttribute("userModerationActions", java.util.Collections.emptyList());
            }
        } catch (Exception ex) {
            log.warn("User {} not found or error loading detail", userId, ex);
            model.addAttribute("user", null);
            model.addAttribute("errorMessage", "Không tìm thấy người dùng #" + userId);
        }
        return "admin/users/detail";
    }

    @GetMapping("/posts")
    public String posts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) Long communityId,
            @RequestParam(required = false) String contentState,
            @RequestParam(required = false) Boolean reportedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            Model model
    ) {
        populateCommonAttributes(model, "posts", "Quản lý bài viết");
        try {
            Pageable pageable = AdminSortHelper.createPageable(page, size, sort, AdminSortHelper.POST_SORT_FIELDS, "createdAt");
            Page<AdminPostSummaryResponse> postPage = adminPostService.getPosts(
                    q, authorId, communityId, contentState, reportedOnly, pageable
            );
            model.addAttribute("posts", postPage.getContent());
            model.addAttribute("pageMeta", PageMeta.from(postPage));
        } catch (Exception ex) {
            log.error("Failed to load posts list", ex);
            model.addAttribute("posts", null);
            model.addAttribute("pageMeta", null);
            model.addAttribute("errorMessage", "Không thể tải danh sách bài viết.");
        }
        model.addAttribute("q", q != null ? q : "");
        model.addAttribute("authorId", authorId);
        model.addAttribute("communityId", communityId);
        model.addAttribute("contentState", contentState != null ? contentState : "");
        model.addAttribute("reportedOnly", reportedOnly);
        model.addAttribute("sort", sort);
        model.addAttribute("size", size);
        return "admin/posts/index";
    }

    @GetMapping("/posts/{postId}")
    public String postDetail(@PathVariable Long postId, Model model) {
        populateCommonAttributes(model, "posts", "Chi tiết bài viết");
        try {
            AdminPostDetailResponse post = adminPostService.getPostDetail(postId);
            model.addAttribute("post", post);

            try {
                Page<ModerationActionResponse> postActions = adminModerationService.getPostModerationActions(
                        postId, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
                );
                model.addAttribute("postModerationActions", postActions.getContent());
            } catch (Exception ex) {
                log.warn("Failed to load moderation actions for post {}", postId, ex);
                model.addAttribute("postModerationActions", java.util.Collections.emptyList());
            }
        } catch (Exception ex) {
            log.warn("Post {} not found or error loading detail", postId, ex);
            model.addAttribute("post", null);
            model.addAttribute("errorMessage", "Không tìm thấy bài viết #" + postId);
        }
        return "admin/posts/detail";
    }

    @GetMapping("/reports")
    public String reports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) ReportReason reason,
            @RequestParam(required = false) Long reviewedBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            Model model
    ) {
        populateCommonAttributes(model, "reports", "Hàng đợi báo cáo");
        try {
            Pageable pageable = AdminSortHelper.createPageable(page, size, sort, AdminSortHelper.REPORT_SORT_FIELDS, "createdAt");
            Page<AdminReportSummaryResponse> reportPage = adminReportService.getReports(
                    status, targetType, reason, reviewedBy, pageable
            );
            model.addAttribute("reports", reportPage.getContent());
            model.addAttribute("pageMeta", PageMeta.from(reportPage));
        } catch (Exception ex) {
            log.error("Failed to load reports list", ex);
            model.addAttribute("reports", null);
            model.addAttribute("pageMeta", null);
            model.addAttribute("errorMessage", "Không thể tải danh sách báo cáo.");
        }
        model.addAttribute("status", status != null ? status.name() : "");
        model.addAttribute("targetType", targetType != null ? targetType : "");
        model.addAttribute("reason", reason != null ? reason.name() : "");
        model.addAttribute("reviewedBy", reviewedBy);
        model.addAttribute("sort", sort);
        model.addAttribute("size", size);
        return "admin/reports/index";
    }

    @GetMapping("/reports/{reportId}")
    public String reportDetail(@PathVariable Long reportId, Model model) {
        populateCommonAttributes(model, "reports", "Chi tiết báo cáo");
        boolean targetCanAtomicRemove = false;
        try {
            AdminReportDetailResponse report = adminReportService.getReportDetail(reportId);
            model.addAttribute("report", report);

            if (report != null && report.target() != null && "POST".equalsIgnoreCase(report.target().type())) {
                try {
                    AdminPostDetailResponse targetPost = adminPostService.getPostDetail(report.target().id());
                    if (targetPost != null && targetPost.capabilities() != null
                            && targetPost.capabilities().canRemove()
                            && targetPost.visibility() == PostVisibility.PUBLIC) {
                        targetCanAtomicRemove = true;
                    }
                } catch (Exception ex) {
                    log.debug("Target post {} could not be fetched for atomic removal check: {}", report.target().id(), ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("Report {} not found or error loading detail", reportId, ex);
            model.addAttribute("report", null);
            model.addAttribute("errorMessage", "Không tìm thấy báo cáo #" + reportId);
        }
        model.addAttribute("targetCanAtomicRemove", targetCanAtomicRemove);
        return "admin/reports/detail";
    }

    @GetMapping("/moderation-actions")
    public String moderationActions(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) ModerationTargetType targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) String actionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            Model model
    ) {
        populateCommonAttributes(model, "moderation-actions", "Nhật ký kiểm duyệt");
        try {
            Pageable pageable = AdminSortHelper.createPageable(page, size, sort, AdminSortHelper.AUDIT_SORT_FIELDS, "createdAt");
            Page<ModerationActionResponse> actionPage = adminModerationService.getModerationActions(
                    actorId, targetType, targetId, actionType, pageable
            );
            model.addAttribute("moderationActions", actionPage.getContent());
            model.addAttribute("pageMeta", PageMeta.from(actionPage));
        } catch (Exception ex) {
            log.error("Failed to load moderation actions list", ex);
            model.addAttribute("moderationActions", null);
            model.addAttribute("pageMeta", null);
            model.addAttribute("errorMessage", "Không thể tải nhật ký kiểm duyệt.");
        }
        model.addAttribute("actorId", actorId);
        model.addAttribute("targetType", targetType != null ? targetType.name() : "");
        model.addAttribute("targetId", targetId);
        model.addAttribute("actionType", actionType != null ? actionType : "");
        model.addAttribute("sort", sort);
        model.addAttribute("size", size);
        return "admin/moderation-actions/index";
    }

    private void populateCommonAttributes(Model model, String activeNav, String pageTitle) {
        model.addAttribute("activeNav", activeNav);
        model.addAttribute("pageTitle", pageTitle);
        currentUserProvider.getCurrentPrincipal().ifPresent(principal -> {
            model.addAttribute("currentUser", principal);
        });
        currentUserProvider.getCurrentUserId().ifPresent(id -> {
            model.addAttribute("currentUserId", id);
        });
    }
}
