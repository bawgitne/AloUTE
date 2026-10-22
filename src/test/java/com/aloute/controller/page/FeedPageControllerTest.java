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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        authPageController = new AuthPageController(environment, currentUserProvider);
        feedPageController = new FeedPageController(currentUserProvider, userService, postService);

        // Registered together to test routing ambiguity and ownership
        mockMvcBoth = MockMvcBuilders.standaloneSetup(authPageController, feedPageController).build();
        // Standalone FeedPageController to test /feed isolation
        mockMvcFeedOnly = MockMvcBuilders.standaloneSetup(feedPageController).build();
    }

    private SpringTemplateEngine createTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private WebContext createWebContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockServletContext servletContext = new MockServletContext();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange webExchange = application.buildExchange(request, response);
        return new WebContext(webExchange, Locale.forLanguageTag("vi-VN"));
    }

    private String extractControlLi(String html, String ariaLabel) {
        Pattern pattern = Pattern.compile("<li(?:(?!<li)[\\s\\S])*?aria-label=\"" + Pattern.quote(ariaLabel) + "\"[\\s\\S]*?</li>");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group();
        }
        throw new AssertionError("Could not find <li> containing aria-label=\"" + ariaLabel + "\" in rendered HTML:\n" + html);
    }

    @Test
    @DisplayName("Root route GET / is owned solely by AuthPageController and redirects to /login")
    void rootRouteOwnedSolelyByAuthPageController() throws Exception {
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
                .andExpect(model().attribute("isFeedPage", true))
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
                .andExpect(model().attribute("isFeedPage", true))
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
                .andExpect(model().attribute("isFeedPage", true))
                .andExpect(model().attribute("feedError", true))
                .andExpect(model().attribute("currentUser", profile))
                .andExpect(model().attribute("posts", empty()))
                .andReturn();

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

    @Test
    @DisplayName("GET /feed with absent page query defaults to page index 0 and size 20")
    void feedAbsentPage_defaultsToPageIndex0AndSize20() throws Exception {
        Long userId = 10L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(postService.getRecommendedFeed(eq(userId), eq(0), eq(20)))
                .thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"))
                .andExpect(model().attribute("feedError", false));

        verify(postService).getRecommendedFeed(userId, 0, 20);
    }

    @Test
    @DisplayName("GET /feed?page=0 forwards exact page index 0 and size 20")
    void feedExplicitPage0_reachesExactIndex0AndSize20() throws Exception {
        Long userId = 11L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(postService.getRecommendedFeed(eq(userId), eq(0), eq(20)))
                .thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"));

        verify(postService).getRecommendedFeed(userId, 0, 20);
    }

    @Test
    @DisplayName("GET /feed?page=1 forwards exact page index 1 and size 20")
    void feedPage1_reachesExactIndex1AndSize20() throws Exception {
        Long userId = 12L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(postService.getRecommendedFeed(eq(userId), eq(1), eq(20)))
                .thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"));

        verify(postService).getRecommendedFeed(userId, 1, 20);
    }

    @Test
    @DisplayName("GET /feed?page=5 forwards exact later page index 5 and size 20")
    void feedLaterPage5_reachesExactIndex5AndSize20() throws Exception {
        Long userId = 13L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(postService.getRecommendedFeed(eq(userId), eq(5), eq(20)))
                .thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"));

        verify(postService).getRecommendedFeed(userId, 5, 20);
    }

    @Test
    @DisplayName("GET /feed?page=107374182 reaches maximum safe page boundary without JPA offset overflow")
    void feedMaxSafePage_reachesExactBoundaryIndexAndSize20() throws Exception {
        Long userId = 18L;
        int maxSafePage = 107374182; // floor(Integer.MAX_VALUE / 20)
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(postService.getRecommendedFeed(eq(userId), eq(maxSafePage), eq(20)))
                .thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed").param("page", String.valueOf(maxSafePage)))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"))
                .andExpect(model().attribute("feedError", false));

        verify(postService).getRecommendedFeed(userId, maxSafePage, 20);
    }

    @Test
    @DisplayName("GET /feed?page=107374183 (first unsafe page exceeding Integer.MAX_VALUE offset) safely normalizes to page 0")
    void feedFirstUnsafePage_normalizesSafelyToPage0() throws Exception {
        Long userId = 19L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(postService.getRecommendedFeed(eq(userId), eq(0), eq(20)))
                .thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed").param("page", "107374183"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"))
                .andExpect(model().attribute("feedError", false));

        verify(postService).getRecommendedFeed(userId, 0, 20);
    }

    @Test
    @DisplayName("GET /feed?page=2147483647 (Integer.MAX_VALUE) safely normalizes to page 0 without unsafe service call")
    void feedIntegerMaxPage_normalizesSafelyToPage0() throws Exception {
        Long userId = 20L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(postService.getRecommendedFeed(eq(userId), eq(0), eq(20)))
                .thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed").param("page", String.valueOf(Integer.MAX_VALUE)))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"))
                .andExpect(model().attribute("feedError", false));

        verify(postService).getRecommendedFeed(userId, 0, 20);
    }

    @Test
    @DisplayName("GET /feed?page=-5 safely normalizes negative page to page index 0 without 500 error")
    void feedNegativePage_normalizesSafelyToPage0() throws Exception {
        Long userId = 14L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(postService.getRecommendedFeed(eq(userId), eq(0), eq(20)))
                .thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed").param("page", "-5"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"))
                .andExpect(model().attribute("feedError", false));

        verify(postService).getRecommendedFeed(userId, 0, 20);
    }

    @Test
    @DisplayName("GET /feed?page=invalid_string safely normalizes non-integer to page index 0 without 500 error")
    void feedNonIntegerPage_normalizesSafelyToPage0() throws Exception {
        Long userId = 15L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(postService.getRecommendedFeed(eq(userId), eq(0), eq(20)))
                .thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed").param("page", "invalid_string"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"))
                .andExpect(model().attribute("feedError", false));

        verify(postService).getRecommendedFeed(userId, 0, 20);
    }

    @Test
    @DisplayName("GET /feed?page=999999999999999999999999999 safely normalizes overflow to page index 0 without 500 error")
    void feedOverflowPage_normalizesSafelyToPage0() throws Exception {
        Long userId = 16L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(postService.getRecommendedFeed(eq(userId), eq(0), eq(20)))
                .thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed").param("page", "999999999999999999999999999"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"))
                .andExpect(model().attribute("feedError", false));

        verify(postService).getRecommendedFeed(userId, 0, 20);
    }

    @Test
    @DisplayName("GET /feed?page=  normalizes whitespace-only page to page index 0")
    void feedWhitespacePage_normalizesSafelyToPage0() throws Exception {
        Long userId = 17L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(postService.getRecommendedFeed(eq(userId), eq(0), eq(20)))
                .thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed").param("page", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"));

        verify(postService).getRecommendedFeed(userId, 0, 20);
    }

    @Test
    @DisplayName("Anonymous user navigation forwards null userId with requested page")
    void feedAnonymousUser_forwardsNullUserIdWithRequestedPage() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());
        when(postService.getRecommendedFeed(eq(null), eq(2), eq(20)))
                .thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"))
                .andExpect(model().attributeDoesNotExist("currentUser"))
                .andExpect(model().attribute("activeNav", "feed"))
                .andExpect(model().attribute("isFeedPage", true));

        verify(postService).getRecommendedFeed(null, 2, 20);
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("Authenticated user navigation loads profile and forwards userId with requested page")
    void feedCurrentUser_populatesProfileAndForwardsUserId() throws Exception {
        Long userId = 42L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        ProfileResponse profile = mock(ProfileResponse.class);
        when(userService.getProfile(userId)).thenReturn(profile);

        when(postService.getRecommendedFeed(eq(userId), eq(3), eq(20)))
                .thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvcFeedOnly.perform(get("/feed").param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed/index"))
                .andExpect(model().attribute("currentUser", profile))
                .andExpect(model().attribute("activeNav", "feed"))
                .andExpect(model().attribute("isFeedPage", true));

        verify(userService).getProfile(userId);
        verify(postService).getRecommendedFeed(userId, 3, 20);
    }

    @Test
    @DisplayName("Explicit distinct posts per page: Page 0 returns Post A, Page 1 returns Post B without duplication")
    void feedDistinctPostsPerPage_noDuplicateOrMergedContent() throws Exception {
        Long userId = 50L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));

        PostResponse postA = mock(PostResponse.class);
        PageMeta meta0 = new PageMeta(0, 20, 2L, 2, false, true);
        when(postService.getRecommendedFeed(eq(userId), eq(0), eq(20)))
                .thenReturn(ApiResponse.page(List.of(postA), meta0));

        PostResponse postB = mock(PostResponse.class);
        PageMeta meta1 = new PageMeta(1, 20, 2L, 2, true, false);
        when(postService.getRecommendedFeed(eq(userId), eq(1), eq(20)))
                .thenReturn(ApiResponse.page(List.of(postB), meta1));

        MvcResult res0 = mockMvcFeedOnly.perform(get("/feed").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("posts", hasSize(1)))
                .andExpect(model().attribute("pageMeta", meta0))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<PostResponse> posts0 = (List<PostResponse>) res0.getModelAndView().getModel().get("posts");
        assertThat(posts0.get(0)).isSameAs(postA);

        MvcResult res1 = mockMvcFeedOnly.perform(get("/feed").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("posts", hasSize(1)))
                .andExpect(model().attribute("pageMeta", meta1))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<PostResponse> posts1 = (List<PostResponse>) res1.getModelAndView().getModel().get("posts");
        assertThat(posts1.get(0)).isSameAs(postB);
        assertThat(posts0.get(0)).isNotSameAs(posts1.get(0));
    }

    @Test
    @DisplayName("Render: fragments/pagination on page 0 binds disabled state to Previous control and enabled href to Next control")
    void renderPaginationPage0_elementSpecificPreviousDisabledAndNextPointsToPage1() {
        SpringTemplateEngine engine = createTemplateEngine();
        WebContext ctx = createWebContext();
        PageMeta pageMeta = new PageMeta(0, 20, 60L, 3, true, false); // hasNext=true, hasPrevious=false
        ctx.setVariable("pageMeta", pageMeta);
        ctx.setVariable("baseUrl", "/feed");

        String html = engine.process("fragments/pagination", Collections.singleton("pagination"), ctx);

        // Previous control: element-specific isolation
        String prevLi = extractControlLi(html, "Trang trước");
        assertThat(prevLi).contains("class=\"page-item disabled\"");
        assertThat(prevLi).contains("aria-disabled=\"true\"");
        assertThat(prevLi).contains("tabindex=\"-1\"");
        assertThat(prevLi).contains("aria-label=\"Trang trước\"");
        assertThat(prevLi).contains("href=\"/feed?page=-1\"");

        // Next control: element-specific isolation
        String nextLi = extractControlLi(html, "Trang tiếp");
        assertThat(nextLi).doesNotContain("disabled");
        assertThat(nextLi).contains("href=\"/feed?page=1\"");
        assertThat(nextLi).doesNotContain("aria-disabled=\"true\"");
        assertThat(nextLi).doesNotContain("tabindex=\"-1\"");
        assertThat(nextLi).contains("aria-label=\"Trang tiếp\"");
    }

    @Test
    @DisplayName("Render: fragments/pagination on page 1 binds enabled hrefs to both Previous (/feed?page=0) and Next (/feed?page=2) controls")
    void renderPaginationPage1_elementSpecificPreviousPointsToPage0AndNextPointsToPage2() {
        SpringTemplateEngine engine = createTemplateEngine();
        WebContext ctx = createWebContext();
        PageMeta pageMeta = new PageMeta(1, 20, 60L, 3, true, true); // hasNext=true, hasPrevious=true
        ctx.setVariable("pageMeta", pageMeta);
        ctx.setVariable("baseUrl", "/feed");

        String html = engine.process("fragments/pagination", Collections.singleton("pagination"), ctx);

        // Previous control: element-specific isolation
        String prevLi = extractControlLi(html, "Trang trước");
        assertThat(prevLi).doesNotContain("disabled");
        assertThat(prevLi).contains("href=\"/feed?page=0\"");
        assertThat(prevLi).doesNotContain("aria-disabled=\"true\"");
        assertThat(prevLi).doesNotContain("tabindex=\"-1\"");
        assertThat(prevLi).contains("aria-label=\"Trang trước\"");

        // Next control: element-specific isolation
        String nextLi = extractControlLi(html, "Trang tiếp");
        assertThat(nextLi).doesNotContain("disabled");
        assertThat(nextLi).contains("href=\"/feed?page=2\"");
        assertThat(nextLi).doesNotContain("aria-disabled=\"true\"");
        assertThat(nextLi).doesNotContain("tabindex=\"-1\"");
        assertThat(nextLi).contains("aria-label=\"Trang tiếp\"");
    }

    @Test
    @DisplayName("Render: fragments/pagination on final page binds enabled href to Previous control and disabled state to Next control")
    void renderPaginationFinalPage_elementSpecificNextDisabledAndPreviousPointsToPrecedingPage() {
        SpringTemplateEngine engine = createTemplateEngine();
        WebContext ctx = createWebContext();
        PageMeta pageMeta = new PageMeta(2, 20, 60L, 3, false, true); // hasNext=false, hasPrevious=true
        ctx.setVariable("pageMeta", pageMeta);
        ctx.setVariable("baseUrl", "/feed");

        String html = engine.process("fragments/pagination", Collections.singleton("pagination"), ctx);

        // Previous control: element-specific isolation
        String prevLi = extractControlLi(html, "Trang trước");
        assertThat(prevLi).doesNotContain("disabled");
        assertThat(prevLi).contains("href=\"/feed?page=1\"");
        assertThat(prevLi).doesNotContain("aria-disabled=\"true\"");
        assertThat(prevLi).doesNotContain("tabindex=\"-1\"");
        assertThat(prevLi).contains("aria-label=\"Trang trước\"");

        // Next control: element-specific isolation
        String nextLi = extractControlLi(html, "Trang tiếp");
        assertThat(nextLi).contains("class=\"page-item disabled\"");
        assertThat(nextLi).contains("aria-disabled=\"true\"");
        assertThat(nextLi).contains("tabindex=\"-1\"");
        assertThat(nextLi).contains("aria-label=\"Trang tiếp\"");
        assertThat(nextLi).contains("href=\"/feed?page=3\"");
    }

    @Test
    @DisplayName("Render: fragments/pagination with totalPages <= 1 renders nothing (no pagination nav)")
    void renderPaginationSinglePage_doesNotRenderPaginationNav() {
        SpringTemplateEngine engine = createTemplateEngine();
        WebContext ctx = createWebContext();
        PageMeta pageMeta = new PageMeta(0, 20, 15L, 1, false, false);
        ctx.setVariable("pageMeta", pageMeta);
        ctx.setVariable("baseUrl", "/feed");

        String html = engine.process("fragments/pagination", Collections.singleton("pagination"), ctx);

        // nav element should not be rendered when totalPages <= 1
        assertThat(html.trim()).isEmpty();
    }
}
