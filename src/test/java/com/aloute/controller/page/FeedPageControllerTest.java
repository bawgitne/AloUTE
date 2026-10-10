package com.aloute.controller.page;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.dto.response.PostResponse;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.PostService;
import com.aloute.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FeedPageControllerTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private UserService userService;

    @Mock
    private PostService postService;

    @Mock
    private Environment environment;

    private AuthPageController authPageController;
    private FeedPageController feedPageController;
    private MockMvc mockMvcBoth;
    private MockMvc mockMvcFeedOnly;

    @BeforeEach
    void setUp() {
        authPageController = new AuthPageController(environment);
        feedPageController = new FeedPageController(currentUserProvider, userService, postService);

        // Registered together to test routing ambiguity and ownership
        mockMvcBoth = MockMvcBuilders.standaloneSetup(authPageController, feedPageController).build();
        // Standalone FeedPageController to test /feed isolation
        mockMvcFeedOnly = MockMvcBuilders.standaloneSetup(feedPageController).build();
    }

    @Test
    @DisplayName("Root route GET / is owned solely by AuthPageController and redirects to /login")
    void rootRouteOwnedSolelyByAuthPageController() throws Exception {
        // Having both controllers registered in mockMvcBoth builds without AmbiguousMappingException.
        // Executing GET / routes strictly to AuthPageController redirecting to /login.
        mockMvcBoth.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("FeedPageController only maps /feed and does not claim /")
    void feedPageControllerDoesNotClaimRoot() throws Exception {
        mockMvcFeedOnly.perform(get("/"))
                .andExpect(status().isNotFound());

        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());
        when(postService.getRecommendedFeed(null, 0, 20)).thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"));
    }

    @Test
    @DisplayName("Feed route GET /feed returns feed/index with posts and error=false when posts exist")
    void feedSuccessWithPosts() throws Exception {
        Long userId = 1L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));

        ProfileResponse profile = mock(ProfileResponse.class);
        when(userService.getProfile(userId)).thenReturn(profile);

        PostResponse post1 = mock(PostResponse.class);
        PageMeta pageMeta = new PageMeta(0, 20, 1L, 1, false, false);
        ApiResponse<List<PostResponse>> apiResponse = ApiResponse.page(List.of(post1), pageMeta);
        when(postService.getRecommendedFeed(eq(userId), eq(0), eq(20))).thenReturn(apiResponse);

        mockMvcFeedOnly.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"))
                .andExpect(model().attribute("activeNav", "feed"))
                .andExpect(model().attribute("feedError", false))
                .andExpect(model().attribute("currentUser", profile))
                .andExpect(model().attribute("posts", hasSize(1)))
                .andExpect(model().attribute("pageMeta", pageMeta));
    }

    @Test
    @DisplayName("Feed success with an empty list sets posts=[] and feedError=false without masquerading as failure")
    void feedSuccessWithEmptyList() throws Exception {
        Long userId = 2L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));

        ProfileResponse profile = mock(ProfileResponse.class);
        when(userService.getProfile(userId)).thenReturn(profile);

        when(postService.getRecommendedFeed(eq(userId), eq(0), eq(20)))
                .thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"))
                .andExpect(model().attribute("activeNav", "feed"))
                .andExpect(model().attribute("feedError", false))
                .andExpect(model().attribute("currentUser", profile))
                .andExpect(model().attribute("posts", empty()));
    }

    @Test
    @DisplayName("Feed PostService exception sets error state, retains safe user-facing behavior, and does not expose technical exception details")
    void feedPostServiceExceptionSetsErrorStateSafely() throws Exception {
        Long userId = 3L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));

        ProfileResponse profile = mock(ProfileResponse.class);
        when(userService.getProfile(userId)).thenReturn(profile);

        String technicalSecretError = "Connection refused to database: secret_db_token_12345";
        when(postService.getRecommendedFeed(eq(userId), anyInt(), anyInt()))
                .thenThrow(new RuntimeException(technicalSecretError));

        MvcResult result = mockMvcFeedOnly.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"))
                .andExpect(model().attribute("activeNav", "feed"))
                .andExpect(model().attribute("feedError", true))
                .andExpect(model().attribute("currentUser", profile))
                .andExpect(model().attribute("posts", empty()))
                .andReturn();

        // Verify model does NOT expose technical exception text or raw exception objects to the view
        Map<String, Object> modelMap = result.getModelAndView().getModel();
        assertThat(modelMap).doesNotContainKey("exception");
        assertThat(modelMap).doesNotContainKey("errorMessage");
        assertThat(modelMap).doesNotContainKey("errorDetails");

        for (Object value : modelMap.values()) {
            if (value instanceof String str) {
                assertThat(str).doesNotContain("secret_db_token_12345");
                assertThat(str).doesNotContain("Connection refused");
            }
            assertThat(value).isNotInstanceOf(Throwable.class);
        }
    }
}
