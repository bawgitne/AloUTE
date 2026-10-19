package com.aloute.controller.api.admin;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.common.exception.GlobalExceptionHandler;
import com.aloute.dto.response.admin.AdminPostSummaryResponse;
import com.aloute.entity.PostVisibility;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.admin.AdminPostService;
import com.aloute.service.admin.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerTest {

    private AdminUserService adminUserService;
    private AdminPostService adminPostService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminUserService = mock(AdminUserService.class);
        adminPostService = mock(AdminPostService.class);
        currentUserProvider = mock(CurrentUserProvider.class);

        AdminUserController controller = new AdminUserController(
                adminUserService,
                adminPostService,
                currentUserProvider
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/admin/users/{userId}/posts returns 200 with AdminPostSummaryPageEnvelope")
    void getUserPostsReturns200WithEnvelope() throws Exception {
        when(currentUserProvider.requireUserId()).thenReturn(1L);

        AdminPostSummaryResponse postSummary = new AdminPostSummaryResponse(
                101L,
                null,
                "Excerpt of post 101",
                null,
                null,
                null,
                PostVisibility.PUBLIC,
                "ACTIVE",
                0L,
                Instant.now(),
                new AdminPostSummaryResponse.PostCapabilities(true, false)
        );
        Page<AdminPostSummaryResponse> page = new PageImpl<>(List.of(postSummary), PageRequest.of(0, 20), 1L);
        when(adminPostService.getUserPosts(eq(42L), any(), any(Pageable.class), eq(1L)))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/users/42/posts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(101))
                .andExpect(jsonPath("$.data[0].contentState").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].contentExcerpt").value("Excerpt of post 101"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/admin/users/{userId}/posts with unknown user returns 404 USER_NOT_FOUND")
    void getUserPostsUnknownUserReturns404() throws Exception {
        when(currentUserProvider.requireUserId()).thenReturn(1L);
        when(adminPostService.getUserPosts(eq(99999L), any(), any(Pageable.class), eq(1L)))
                .thenThrow(new ApiException(ErrorCode.USER_NOT_FOUND, "User not found"));

        mockMvc.perform(get("/api/admin/users/99999/posts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/admin/users/{userId}/posts with invalid contentState returns 400 BAD_REQUEST")
    void getUserPostsInvalidContentStateReturns400() throws Exception {
        when(currentUserProvider.requireUserId()).thenReturn(1L);
        when(adminPostService.getUserPosts(eq(42L), eq("INVALID_STATE"), any(Pageable.class), eq(1L)))
                .thenThrow(new ApiException(ErrorCode.BAD_REQUEST, "Invalid contentState: 'INVALID_STATE'"));

        mockMvc.perform(get("/api/admin/users/42/posts")
                        .param("contentState", "INVALID_STATE")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }
}
