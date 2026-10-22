package com.aloute.controller.page;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.CommunityResponse;
import com.aloute.dto.response.PostResponse;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.entity.CommunityRole;
import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.CommunityService;
import com.aloute.service.PostService;
import com.aloute.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CommunityPageControllerTest {

    @Mock
    private CommunityService communityService;

    @Mock
    private PostService postService;

    @Mock
    private UserService userService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private CommunityPageController communityPageController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        communityPageController = new CommunityPageController(
                communityService,
                postService,
                userService,
                currentUserProvider
        );
        mockMvc = MockMvcBuilders.standaloneSetup(communityPageController).build();
    }

    @Test
    @DisplayName("showCommunities renders community index with search query and list")
    void testShowCommunities() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(100L));
        ProfileResponse profile = new ProfileResponse(
                100L, "testuser", "Test User", "bio", null, null, false, false, 0, 0, 0, null
        );
        when(userService.getProfile(100L)).thenReturn(profile);
        when(communityService.list(any(), anyInt(), anyInt(), eq(100L)))
                .thenReturn(ApiResponse.success(List.of()));

        mockMvc.perform(get("/communities").param("q", "tech"))
                .andExpect(status().isOk())
                .andExpect(view().name("community/index"))
                .andExpect(model().attribute("searchQuery", "tech"))
                .andExpect(model().attribute("activeNav", "communities"))
                .andExpect(model().attribute("currentUser", profile))
                .andExpect(model().attributeExists("communities"));
    }

    @Test
    @DisplayName("showCommunityDetail sets hideGlobalCreatePost and isCommunityDetailPage model attributes")
    void testShowCommunityDetailSuccess() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(100L));
        ProfileResponse profile = new ProfileResponse(
                100L, "testuser", "Test User", "bio", null, null, false, false, 0, 0, 0, null
        );
        when(userService.getProfile(100L)).thenReturn(profile);

        CommunityResponse comm = new CommunityResponse(
                1L, 100L, "AI Club", "ai-club", "Mô tả", null, null, false, 25L, 5L, CommunityRole.ADMIN, true
        );
        when(communityService.getBySlug("ai-club", 100L)).thenReturn(comm);
        when(postService.communityPosts(eq(1L), anyInt(), anyInt(), eq(100L)))
                .thenReturn(ApiResponse.success(List.of()));

        mockMvc.perform(get("/c/ai-club"))
                .andExpect(status().isOk())
                .andExpect(view().name("community/detail"))
                .andExpect(model().attribute("community", comm))
                .andExpect(model().attribute("activeNav", "communities"))
                .andExpect(model().attribute("hideGlobalCreatePost", true))
                .andExpect(model().attribute("isCommunityDetailPage", true))
                .andExpect(model().attribute("currentUser", profile));
    }

    @Test
    @DisplayName("showCommunityDetail with numeric ID redirects to canonical slug URL")
    void testShowCommunityDetailNumericIdRedirectsToSlug() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(100L));
        CommunityResponse comm = new CommunityResponse(
                1L, 100L, "AI Club", "ai-club", "Mô tả", null, null, false, 25L, 5L, CommunityRole.ADMIN, true
        );
        when(communityService.get(1L, 100L)).thenReturn(comm);

        mockMvc.perform(get("/communities/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/communities/ai-club"));
    }

    @Test
    @DisplayName("showCommunityDetail handles 404 ApiException gracefully")
    void testShowCommunityDetailNotFound() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());
        when(communityService.getBySlug("non-existent", null))
                .thenThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Community not found"));

        mockMvc.perform(get("/c/non-existent"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("community/detail"))
                .andExpect(model().attribute("hideGlobalCreatePost", true))
                .andExpect(model().attribute("isCommunityDetailPage", true))
                .andExpect(model().attribute("community", nullValue()));
    }
}
