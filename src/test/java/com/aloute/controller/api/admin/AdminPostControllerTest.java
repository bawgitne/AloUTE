package com.aloute.controller.api.admin;

import com.aloute.dto.request.admin.AdminRemovePostRequest;
import com.aloute.dto.response.admin.PostRemovalActionResponse;
import com.aloute.entity.ReportReason;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.admin.AdminModerationService;
import com.aloute.service.admin.AdminPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
