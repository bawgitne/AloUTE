package com.aloute.controller.api.admin;

import com.aloute.dto.response.UserSummary;
import com.aloute.dto.request.admin.AdminRemovePostRequest;
import com.aloute.dto.response.admin.AdminPostDetailResponse;
import com.aloute.dto.response.admin.AdminPostSummaryResponse;
import com.aloute.dto.response.admin.PostRemovalActionResponse;
import com.aloute.entity.PostVisibility;
import com.aloute.entity.ReportReason;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.admin.AdminModerationService;
import com.aloute.service.admin.AdminPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPostControllerTest {

    private AdminPostService adminPostService;
    private AdminModerationService adminModerationService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminPostService = mock(AdminPostService.class);
        adminModerationService = mock(AdminModerationService.class);
        currentUserProvider = mock(CurrentUserProvider.class);

        AdminPostController controller = new AdminPostController(adminPostService, adminModerationService, currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/admin/posts passes authenticated adminId to adminPostService")
    void getPostsPassesAuthenticatedAdminId() throws Exception {
        when(currentUserProvider.requireUserId()).thenReturn(1L);

        UserSummary author = new UserSummary(2L, "author", "Author", null, true);
        AdminPostSummaryResponse summary = new AdminPostSummaryResponse(
                10L, author, "Public excerpt", null, null, null,
                PostVisibility.PUBLIC, "ACTIVE", 0L, Instant.now(),
                new AdminPostSummaryResponse.PostCapabilities(true, false)
        );
        when(adminPostService.getPosts(eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/admin/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].contentExcerpt").value("Public excerpt"));
    }

    @Test
    @DisplayName("GET /api/admin/posts/{postId} passes authenticated adminId to adminPostService")
    void getPostDetailPassesAuthenticatedAdminId() throws Exception {
        when(currentUserProvider.requireUserId()).thenReturn(1L);

        UserSummary author = new UserSummary(2L, "author", "Author", null, true);
        AdminPostDetailResponse detail = new AdminPostDetailResponse(
                10L, author, "Full post content", PostVisibility.PUBLIC, "ACTIVE",
                null, null, Instant.now(), null, Collections.emptyList(), null,
                new AdminPostDetailResponse.PostCapabilities(true, false)
        );
        when(adminPostService.getPostDetail(eq(1L), eq(10L))).thenReturn(detail);

        mockMvc.perform(get("/api/admin/posts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.content").value("Full post content"));
    }

    @Test
    @DisplayName("POST /api/admin/posts/{postId}/remove removes post and returns PostRemovalActionResponse")
    void removePostSuccess() throws Exception {
        when(currentUserProvider.requireUserId()).thenReturn(1L);

        PostRemovalActionResponse response = new PostRemovalActionResponse(
                100L, 10L, "POST_REMOVE", "ADMIN_REMOVED", 200L, Instant.now()
        );
        when(adminModerationService.removePost(eq(1L), eq(10L), any(AdminRemovePostRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/admin/posts/10/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reasonCode": "SPAM",
                                  "reasonDetail": "Automated spam advertising detected",
                                  "publicNotice": "Post removed by moderator"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.actionId").value(100))
                .andExpect(jsonPath("$.data.postId").value(10))
                .andExpect(jsonPath("$.data.actionType").value("POST_REMOVE"))
                .andExpect(jsonPath("$.data.contentState").value("ADMIN_REMOVED"))
                .andExpect(jsonPath("$.data.removalId").value(200));
    }
}
