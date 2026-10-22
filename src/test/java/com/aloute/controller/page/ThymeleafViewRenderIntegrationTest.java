package com.aloute.controller.page;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.dto.response.*;
import com.aloute.entity.CommunityRole;
import com.aloute.entity.MediaType;
import com.aloute.entity.PostVisibility;
import com.aloute.entity.User;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.CommunityService;
import com.aloute.service.PostService;
import com.aloute.service.TopicService;
import com.aloute.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class ThymeleafViewRenderIntegrationTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @MockBean
    private CommunityService communityService;

    @MockBean
    private PostService postService;

    @MockBean
    private UserService userService;

    @MockBean
    private TopicService topicService;

    private WebContext createWebContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockServletContext servletContext = new MockServletContext();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange webExchange = application.buildExchange(request, response);
        return new WebContext(webExchange, Locale.forLanguageTag("vi-VN"));
    }

    private Authentication createMockAuth(Long userId, String username) {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
        user.setUsername(username);
        user.setEmail(username + "@ute.udn.vn");
        user.setPasswordHash("hash1234");
        user.setActive(true);
        user.setRole(Role.USER);
        UserPrincipal principal = UserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private PostResponse createSamplePost(Long id, String content, Instant createdAt) {
        UserSummary author = new UserSummary(10L, "sinhvien_ute", "Nguyễn Văn Sinh Viên", "/images/avatar-fallback.svg", true);
        PostCapabilitiesResponse capabilities = new PostCapabilitiesResponse(true, true, true, true);
        PostViewerStateResponse viewerState = new PostViewerStateResponse(false, false, false);
        return new PostResponse(
                id,
                author,
                content,
                null,
                null,
                null,
                PostVisibility.PUBLIC,
                true,
                true,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                5L,
                2L,
                1L,
                0L,
                100L,
                viewerState,
                capabilities,
                createdAt,
                createdAt
        );
    }

    @Test
    @DisplayName("community/index renders with populated communities, correct memberCount, and /communities/{id} links")
    void testCommunityIndexPopulated() {
        WebContext context = createWebContext();
        CommunityResponse comm = new CommunityResponse(
                42L,
                1L,
                "CLB Lập trình AloUTE",
                "clb-lap-trinh",
                "Cộng đồng chia sẻ kiến thức phần mềm",
                "/images/avatar-fallback.svg",
                null,
                false,
                128L,
                15L,
                CommunityRole.MEMBER,
                false
        );
        context.setVariable("communities", List.of(comm));
        context.setVariable("currentUser", null);

        String html = templateEngine.process("community/index", context);

        assertThat(html).contains("Cộng đồng &amp; Câu lạc bộ");
        assertThat(html).contains("CLB Lập trình AloUTE");
        assertThat(html).contains("c/clb-lap-trinh");
        assertThat(html).contains("/communities/clb-lap-trinh");
        assertThat(html).contains("128");
        assertThat(html).contains("thành viên");
        assertThat(html).doesNotContain("/c/clb-lap-trinh");
        assertThat(html).doesNotContain("th:onclick");
    }

    @Test
    @DisplayName("community/index renders empty state gracefully when communities list is empty")
    void testCommunityIndexEmpty() {
        WebContext context = createWebContext();
        context.setVariable("communities", Collections.emptyList());
        context.setVariable("currentUser", null);

        String html = templateEngine.process("community/index", context);

        assertThat(html).contains("Chưa có cộng đồng nào");
        assertThat(html).contains("Hiện chưa có cộng đồng nào được tạo trên hệ thống AloUTE.");
    }

    @Test
    @DisplayName("community/detail renders populated community and post card stream")
    void testCommunityDetailPopulated() {
        WebContext context = createWebContext();
        CommunityResponse comm = new CommunityResponse(
                7L,
                2L,
                "CLB Trí tuệ nhân tạo",
                "clb-ai",
                "Nghiên cứu mô hình ngôn ngữ lớn",
                "/images/avatar-fallback.svg",
                null,
                false,
                95L,
                8L,
                CommunityRole.ADMIN,
                true
        );
        Instant now = Instant.parse("2026-10-11T14:30:00Z");
        PostResponse post = createSamplePost(101L, "Chào mừng đến với CLB AI!", now);

        context.setVariable("community", comm);
        context.setVariable("posts", List.of(post));
        context.setVariable("currentUser", null);

        String html = templateEngine.process("community/detail", context);

        assertThat(html).contains("CLB Trí tuệ nhân tạo");
        assertThat(html).contains("c/clb-ai");
        assertThat(html).contains("95");
        assertThat(html).contains("Thành viên");
        assertThat(html).contains("Chào mừng đến với CLB AI!");
        assertThat(html).contains("11/10/2026 21:30");
        assertThat(html).doesNotContain("unsplash.com");
        assertThat(html).contains("<div class=\"p-3 pt-0 position-relative\" style=\"z-index: 1;\">");
    }

    @Test
    @DisplayName("community/detail renders empty posts state when posts list is empty")
    void testCommunityDetailEmptyPosts() {
        WebContext context = createWebContext();
        CommunityResponse comm = new CommunityResponse(
                8L,
                2L,
                "CLB Robot UTE",
                "clb-robot",
                "Thi đấu robot sinh viên",
                null,
                null,
                false,
                40L,
                0L,
                CommunityRole.MEMBER,
                false
        );

        context.setVariable("community", comm);
        context.setVariable("posts", Collections.emptyList());
        context.setVariable("currentUser", null);

        String html = templateEngine.process("community/detail", context);

        assertThat(html).contains("CLB Robot UTE");
        assertThat(html).contains("Chưa có bài viết nào");
        assertThat(html).contains("Hãy là người đầu tiên chia sẻ bài viết hoặc câu hỏi trong cộng đồng này!");
    }

    @Test
    @DisplayName("community/detail renders safe not-found state when community is null")
    void testCommunityDetailNullCommunity() {
        WebContext context = createWebContext();
        context.setVariable("community", null);
        context.setVariable("posts", Collections.emptyList());
        context.setVariable("currentUser", null);

        String html = templateEngine.process("community/detail", context);

        assertThat(html).contains("Không tìm thấy cộng đồng");
        assertThat(html).contains("Cộng đồng bạn yêu cầu không tồn tại, đã bị xóa hoặc bạn không có quyền truy cập.");
    }

    @Test
    @DisplayName("error/404 renders Light Campus Vietnamese template with path affordance")
    void testError404Rendering() {
        WebContext context = createWebContext();
        context.setVariable("path", "/duong-dan-khong-ton-tai");
        context.setVariable("currentUser", null);

        String html = templateEngine.process("error/404", context);

        assertThat(html).contains("404 — Không tìm thấy trang");
        assertThat(html).contains("/duong-dan-khong-ton-tai");
        assertThat(html).contains("Về bảng tin chính");
    }

    @Test
    @DisplayName("error/500 renders Light Campus Vietnamese template with reload affordance")
    void testError500Rendering() {
        WebContext context = createWebContext();
        context.setVariable("currentUser", null);

        String html = templateEngine.process("error/500", context);

        assertThat(html).contains("500 — Đã xảy ra sự cố hệ thống");
        assertThat(html).contains("Tải lại trang");
        assertThat(html).contains("Về bảng tin chính");
    }

    @Test
    @DisplayName("user/profile renders honest unavailable state when profilePosts is null (pending backend integration)")
    void testUserProfilePostsUnavailable() {
        WebContext context = createWebContext();
        ProfileResponse profile = new ProfileResponse(
                5L,
                "nguyen_van_b",
                "Nguyễn Văn B",
                "Khoa CNTT",
                null,
                "https://example.com",
                false,
                false,
                10L,
                20L,
                5L,
                null
        );

        context.setVariable("profile", profile);
        context.setVariable("currentUser", profile);
        context.setVariable("profilePosts", null);

        String html = templateEngine.process("user/profile", context);

        assertThat(html).contains("Nguyễn Văn B");
        assertThat(html).contains("Danh sách bài viết chưa khả dụng");
        assertThat(html).contains("Tính năng hiển thị danh sách bài viết theo tác giả hiện đang được hoàn thiện và sẽ sớm khả dụng.");
    }

    @Test
    @DisplayName("post/_post-card renders timestamps using #temporals in Asia/Ho_Chi_Minh timezone")
    void testPostCardTimestampFormatting() {
        WebContext context = createWebContext();
        Instant instant = Instant.parse("2026-10-11T09:15:00Z"); // 09:15 UTC = 16:15 ICT
        PostResponse post = createSamplePost(99L, "Kiểm thử định dạng thời gian", instant);
        context.setVariable("post", post);

        String html = templateEngine.process("post/_post-card", context);

        assertThat(html).contains("11/10/2026 16:15");
        assertThat(html).doesNotContain("2026-10-11T09:15:00Z");
    }

    @Test
    @DisplayName("MockMvc GET /communities returns 200 OK with rendered HTML for authenticated user")
    void testMockMvcGetCommunities() throws Exception {
        Authentication auth = createMockAuth(1L, "user1");
        CommunityResponse comm = new CommunityResponse(
                42L,
                1L,
                "CLB Lập trình AloUTE",
                "clb-lap-trinh",
                "Cộng đồng chia sẻ kiến thức phần mềm",
                "/images/avatar-fallback.svg",
                null,
                false,
                128L,
                15L,
                CommunityRole.MEMBER,
                false
        );
        PageMeta pageMeta = new PageMeta(0, 20, 1L, 1, false, false);
        when(communityService.list(any(), anyInt(), anyInt(), any())).thenReturn(ApiResponse.page(List.of(comm), pageMeta));

        mockMvc.perform(get("/communities").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cộng đồng &amp; Câu lạc bộ")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CLB Lập trình AloUTE")));
    }

    @Test
    @DisplayName("MockMvc GET /communities/{id} returns 200 OK and handles missing community gracefully")
    void testMockMvcGetCommunityDetail() throws Exception {
        Authentication auth = createMockAuth(1L, "user1");
        when(communityService.get(eq(99999L), any())).thenReturn(null);
        when(postService.communityPosts(eq(99999L), anyInt(), anyInt(), any())).thenReturn(ApiResponse.success(Collections.emptyList()));

        mockMvc.perform(get("/communities/99999").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Không tìm thấy cộng đồng")));
    }

    @Test
    @DisplayName("MockMvc GET /error with Accept: text/html and status 404 returns HTTP 404 with rendered error/404 view")
    void testMockMvcHtmlError404() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI, "/nonexistent/page")
                        .accept(org.springframework.http.MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("404 — Không tìm thấy trang")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/nonexistent/page")));
    }

    @Test
    @DisplayName("MockMvc GET /error with Accept: text/html and status 500 returns HTTP 500 with rendered error/500 view")
    void testMockMvcHtmlError500() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .accept(org.springframework.http.MediaType.TEXT_HTML))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("500 — Đã xảy ra sự cố hệ thống")));
    }

    @Test
    @DisplayName("MockMvc GET /error with Accept: application/json preserves API JSON format with correct status")
    void testMockMvcJsonError() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .accept(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON));
    }


    @Test
    @DisplayName("Batch 2: MockMvc GET /search renders search/index template with query and category tabs")
    void testSearchPageRender() throws Exception {
        mockMvc.perform(get("/search")
                        .param("q", "lap-trinh")
                        .param("category", "all")
                        .with(authentication(createMockAuth(1L, "sinhvien1"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("lap-trinh")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cộng đồng")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chủ đề")));
    }

    @Test
    @DisplayName("Batch 2: MockMvc GET /topics/{id} renders topic/detail template")
    void testTopicPageRender() throws Exception {
        mockMvc.perform(get("/topics/1")
                        .with(authentication(createMockAuth(1L, "sinhvien1"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Batch 2: Layout renders Report, Quote, and Create Community modals")
    void testBatch2ModalsRender() {
        WebContext ctx = createWebContext();
        ctx.setVariable("currentUser", null);
        ctx.setVariable("title", "AloUTE");
        String html = templateEngine.process("fragments/modal", Collections.singleton("postModals"), ctx);
        assertThat(html).contains("id=\"reportModal\"");
        assertThat(html).contains("id=\"quotePostModal\"");
        assertThat(html).contains("id=\"createCommunityModal\"");
    }

    @Test
    @DisplayName("Batch 2: Head title formatting avoids duplicated — AloUTE suffix")
    void testHeadTitleDeduplication() {
        WebContext ctx1 = createWebContext();
        ctx1.setVariable("title", "Tin nhắn");
        String html1 = templateEngine.process("fragments/head", Collections.singleton("head"), ctx1);
        assertThat(html1).contains("<title>Tin nhắn — AloUTE</title>");

        WebContext ctx2 = createWebContext();
        ctx2.setVariable("title", "Tin nhắn — AloUTE");
        String html2 = templateEngine.process("fragments/head", Collections.singleton("head"), ctx2);
        assertThat(html2).contains("<title>Tin nhắn — AloUTE</title>");
        assertThat(html2).doesNotContain("Tin nhắn — AloUTE — AloUTE");
    }

    @Test
    @DisplayName("Batch 2: Profile Saved tab is guarded by currentUser.id == profile.id")
    void testProfileSavedTabOwnershipGuard() {
        ProfileResponse profileUser = new ProfileResponse(
                2L, "user2", "Người dùng 2", "Khoa CNTT",
                null, null, false, false, 10L, 5L, 2L, null
        );
        ProfileResponse viewerUser = new ProfileResponse(
                1L, "user1", "Người dùng 1", "Khoa CNTT",
                null, null, false, false, 5L, 2L, 1L, null
        );

        // When viewer != profile owner: saved tab must NOT be rendered
        WebContext ctxOther = createWebContext();
        ctxOther.setVariable("profile", profileUser);
        ctxOther.setVariable("currentUser", viewerUser);
        ctxOther.setVariable("posts", Collections.emptyList());
        ctxOther.setVariable("title", profileUser.displayName());
        String htmlOther = templateEngine.process("user/profile", ctxOther);
        assertThat(htmlOther).doesNotContain("id=\"tab-saved\"");
        assertThat(htmlOther).doesNotContain("id=\"pane-saved\"");

        // When viewer == profile owner: saved tab MUST be rendered
        WebContext ctxOwner = createWebContext();
        ctxOwner.setVariable("profile", profileUser);
        ctxOwner.setVariable("currentUser", profileUser);
        ctxOwner.setVariable("posts", Collections.emptyList());
        ctxOwner.setVariable("title", profileUser.displayName());
        String htmlOwner = templateEngine.process("user/profile", ctxOwner);
        assertThat(htmlOwner).contains("id=\"tab-saved\"");
        assertThat(htmlOwner).contains("id=\"pane-saved\"");
    }

    @Test
    @DisplayName("Batch 2: Search normalizes invalid category and bounds negative page/size")
    void testSearchNormalizationAndBounds() throws Exception {
        mockMvc.perform(get("/search")
                        .param("q", "lap-trinh")
                        .param("category", "invalid_category")
                        .param("page", "-5")
                        .param("size", "1000")
                        .with(authentication(createMockAuth(1L, "sinhvien1"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("lap-trinh")));
    }

    @Test
    @DisplayName("Batch 2: Search renders populated communities and pagination links with PageMeta")
    void testSearchPopulatedAndPagination() throws Exception {
        CommunityResponse comm = new CommunityResponse(
                42L, 1L, "CLB Phần mềm", "clb-phan-mem", "Mô tả", null, null, false, 50L, 10L, CommunityRole.MEMBER, false
        );
        PageMeta meta = new PageMeta(0, 10, 25L, 3, false, true);
        when(communityService.list(eq("phan-mem"), eq(0), eq(10), any()))
                .thenReturn(ApiResponse.success(List.of(comm), meta));

        mockMvc.perform(get("/search")
                        .param("q", "phan-mem")
                        .param("category", "communities")
                        .param("page", "0")
                        .param("size", "10")
                        .with(authentication(createMockAuth(1L, "sinhvien1"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CLB Phần mềm")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/search?q=phan-mem&amp;category=communities&amp;page=1")));
    }

    @Test
    @DisplayName("Batch 2: Search renders retry alert on service failure without false empty state")
    void testSearchServiceErrorDistinction() throws Exception {
        when(communityService.list(eq("error-query"), anyInt(), anyInt(), any()))
                .thenThrow(new RuntimeException("Database timeout"));

        mockMvc.perform(get("/search")
                        .param("q", "error-query")
                        .param("category", "communities")
                        .with(authentication(createMockAuth(1L, "sinhvien1"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Không thể tải danh sách cộng đồng lúc này")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Không tìm thấy cộng đồng nào phù hợp"))));
    }

    @Test
    @DisplayName("Batch 2: Topic detail preserves topic metadata when posts service fails and renders retry alert")
    void testTopicPagePostsServiceFailure() throws Exception {
        TopicResponse topic = new TopicResponse(10L, "Trí tuệ nhân tạo", "tri-tue-nhan-tao");
        when(topicService.getByName("tri-tue-nhan-tao")).thenReturn(topic);
        when(postService.topicPosts(eq(10L), anyInt(), anyInt(), any()))
                .thenThrow(new RuntimeException("Post service unreachable"));

        mockMvc.perform(get("/topics/tri-tue-nhan-tao")
                        .with(authentication(createMockAuth(1L, "sinhvien1"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Trí tuệ nhân tạo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Không thể tải danh sách bài viết thuộc chủ đề lúc này")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Chưa có bài viết nào trong chủ đề này"))));
    }

    @Test
    @DisplayName("Topic detail with numeric ID redirects to canonical slug URL")
    void testTopicDetailNumericIdRedirectsToSlug() throws Exception {
        TopicResponse topic = new TopicResponse(10L, "Trí tuệ nhân tạo", "tri-tue-nhan-tao");
        when(topicService.get(10L)).thenReturn(topic);

        mockMvc.perform(get("/topics/10")
                        .with(authentication(createMockAuth(1L, "sinhvien1"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics/tri-tue-nhan-tao"));
    }

    @Test
    @DisplayName("Batch 2: Interaction bar quote button is strictly fail-closed")
    void testQuoteButtonFailClosedGate() {
        // Case A: allowQuote = true and canQuote = true -> quote button present
        PostResponse postWithQuote = createSamplePost(101L, "Allowed quote post", Instant.now());
        WebContext ctxA = createWebContext();
        ctxA.setVariable("post", postWithQuote);
        String htmlA = templateEngine.process("post/_interaction-bar", Collections.singleton("interactionBar"), ctxA);
        assertThat(htmlA).contains("interaction-btn-quote");
        assertThat(htmlA).contains("data-action=\"open-quote-modal\"");

        // Case B: allowQuote = true but capabilities is null -> MUST FAIL CLOSED
        UserSummary author = new UserSummary(10L, "sinhvien_ute", "Nguyễn Văn Sinh Viên", "/images/avatar-fallback.svg", true);
        PostViewerStateResponse viewerState = new PostViewerStateResponse(false, false, false);
        PostResponse postNullCaps = new PostResponse(
                102L, author, "Null caps post", null, null, null,
                PostVisibility.PUBLIC, true, true, Collections.emptyList(), Collections.emptyList(), null,
                0L, 0L, 0L, 0L, 0L, viewerState, null, Instant.now(), Instant.now()
        );
        WebContext ctxB = createWebContext();
        ctxB.setVariable("post", postNullCaps);
        String htmlB = templateEngine.process("post/_interaction-bar", Collections.singleton("interactionBar"), ctxB);
        assertThat(htmlB).doesNotContain("interaction-btn-quote");
        assertThat(htmlB).doesNotContain("open-quote-modal");

        // Case C: allowQuote = true but canQuote = false -> fail closed
        PostCapabilitiesResponse noQuoteCaps = new PostCapabilitiesResponse(true, true, true, false);
        PostResponse postCannotQuote = new PostResponse(
                103L, author, "Cannot quote post", null, null, null,
                PostVisibility.PUBLIC, true, true, Collections.emptyList(), Collections.emptyList(), null,
                0L, 0L, 0L, 0L, 0L, viewerState, noQuoteCaps, Instant.now(), Instant.now()
        );
        WebContext ctxC = createWebContext();
        ctxC.setVariable("post", postCannotQuote);
        String htmlC = templateEngine.process("post/_interaction-bar", Collections.singleton("interactionBar"), ctxC);
        assertThat(htmlC).doesNotContain("interaction-btn-quote");
        assertThat(htmlC).doesNotContain("open-quote-modal");

        // Case D: allowQuote = false -> fail closed
        PostResponse postQuoteDisabled = new PostResponse(
                104L, author, "Quote disabled post", null, null, null,
                PostVisibility.PUBLIC, true, false, Collections.emptyList(), Collections.emptyList(), null,
                0L, 0L, 0L, 0L, 0L, viewerState, new PostCapabilitiesResponse(true, true, true, true), Instant.now(), Instant.now()
        );
        WebContext ctxD = createWebContext();
        ctxD.setVariable("post", postQuoteDisabled);
        String htmlD = templateEngine.process("post/_interaction-bar", Collections.singleton("interactionBar"), ctxD);
        assertThat(htmlD).doesNotContain("interaction-btn-quote");
        assertThat(htmlD).doesNotContain("open-quote-modal");
    }

    @Test
    @DisplayName("Batch 2: Profile renders honest unavailable states for Replies, Media, and Saved tabs")
    void testProfileUnavailableStatesRender() {
        ProfileResponse profileUser = new ProfileResponse(
                1L, "sinhvien1", "Sinh Viên 1", "Khoa CNTT",
                null, null, false, false, 10L, 5L, 2L, null
        );
        WebContext ctx = createWebContext();
        ctx.setVariable("profile", profileUser);
        ctx.setVariable("currentUser", profileUser);
        ctx.setVariable("posts", Collections.emptyList());
        ctx.setVariable("title", profileUser.displayName());

        String html = templateEngine.process("user/profile", ctx);

        assertThat(html).contains("Danh sách phản hồi chưa khả dụng");
        assertThat(html).contains("Danh sách đa phương tiện chưa khả dụng");
        assertThat(html).contains("Danh sách bài viết đã lưu chưa khả dụng");
        assertThat(html).doesNotContain("Chưa có phản hồi nào");
        assertThat(html).doesNotContain("Chưa có tệp đa phương tiện nào");
        assertThat(html).doesNotContain("Chưa có bài viết đã lưu nào");
    }

    @Test
    @DisplayName("Batch 2: Search size parameter is clamped to 100 max and preserved across form, tabs, and pagination")
    void testSearchSizeBoundedAndPreservedInLinks() throws Exception {
        CommunityResponse comm = new CommunityResponse(
                42L, 1L, "CLB Phần mềm", "clb-phan-mem", "Mô tả", null, null, false, 50L, 10L, CommunityRole.MEMBER, false
        );
        PageMeta meta = new PageMeta(0, 10, 25L, 3, false, true);
        when(communityService.list(eq("phan-mem"), eq(0), eq(10), any()))
                .thenReturn(ApiResponse.success(List.of(comm), meta));

        // Request with size=10
        mockMvc.perform(get("/search")
                        .param("q", "phan-mem")
                        .param("category", "communities")
                        .param("page", "0")
                        .param("size", "10")
                        .with(authentication(createMockAuth(1L, "sinhvien1"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"size\" value=\"10\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("size=10")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/search?q=phan-mem&amp;category=communities&amp;page=1&amp;size=10")));

        // Request with size=500 -> must clamp to max 100
        mockMvc.perform(get("/search")
                        .param("q", "phan-mem")
                        .param("category", "communities")
                        .param("size", "500")
                        .with(authentication(createMockAuth(1L, "sinhvien1"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"size\" value=\"100\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("size=100")));
    }

    @Test
    @DisplayName("Batch 2: Quote post modal supports PUBLIC and FOLLOWERS visibility and excludes COMMUNITY_ONLY")
    void testQuoteModalVisibilityOptions() {
        WebContext ctx = createWebContext();
        ctx.setVariable("currentUser", null);

        String html = templateEngine.process("fragments/modal", ctx);

        assertThat(html).contains("id=\"quotePostModal\"");
        assertThat(html).contains("id=\"quote-post-visibility\"");
        assertThat(html).contains("value=\"PUBLIC\"");
        assertThat(html).contains("value=\"FOLLOWERS\"");
        assertThat(html).doesNotContain("COMMUNITY_ONLY");
    }

    @Test
    @DisplayName("Poll: post/_post-card renders single-choice open poll with radio inputs and without closed badge")
    void testPollSingleChoiceOpenRender() {
        List<PollOptionResponse> options = List.of(
                new PollOptionResponse(101L, "Lựa chọn 1", 5, false),
                new PollOptionResponse(102L, "Lựa chọn 2", 3, false)
        );
        PollResponse poll = new PollResponse(
                50L, "Bạn thích ngôn ngữ nào?", false,
                Instant.now().plusSeconds(86400), Instant.now().plusSeconds(86400),
                8, null, options
        );
        PostResponse postWithPoll = new PostResponse(
                1L, new UserSummary(1L, "u1", "U1", null, true), "Bài viết có poll",
                null, null, null, PostVisibility.PUBLIC, true, true,
                Collections.emptyList(), Collections.emptyList(), poll,
                0L, 0L, 0L, 0L, 0L,
                new PostViewerStateResponse(false, false, false),
                new PostCapabilitiesResponse(true, true, true, true),
                Instant.now(), Instant.now()
        );

        WebContext ctx = createWebContext();
        ctx.setVariable("post", postWithPoll);

        String html = templateEngine.process("post/_post-card", Collections.singleton("postCard"), ctx);

        assertThat(html).contains("Bạn thích ngôn ngữ nào?");
        assertThat(html).contains("class=\"poll-vote-form");
        assertThat(html).contains("type=\"radio\"");
        assertThat(html).contains("data-allow-multiple=\"false\"");
        assertThat(html).contains("data-is-closed=\"false\"");
        assertThat(html).doesNotContain("Đã kết thúc");
    }

    @Test
    @DisplayName("Poll: post/_post-card renders multiple-choice open poll with checkbox inputs")
    void testPollMultipleChoiceOpenRender() {
        List<PollOptionResponse> options = List.of(
                new PollOptionResponse(201L, "Java", 10, false),
                new PollOptionResponse(202L, "TypeScript", 15, false)
        );
        PollResponse poll = new PollResponse(
                60L, "Công nghệ bạn đang dùng?", true,
                Instant.now().plusSeconds(86400), Instant.now().plusSeconds(86400),
                25, null, options
        );
        PostResponse postWithPoll = new PostResponse(
                2L, new UserSummary(1L, "u1", "U1", null, true), "Bài viết multi poll",
                null, null, null, PostVisibility.PUBLIC, true, true,
                Collections.emptyList(), Collections.emptyList(), poll,
                0L, 0L, 0L, 0L, 0L,
                new PostViewerStateResponse(false, false, false),
                new PostCapabilitiesResponse(true, true, true, true),
                Instant.now(), Instant.now()
        );

        WebContext ctx = createWebContext();
        ctx.setVariable("post", postWithPoll);

        String html = templateEngine.process("post/_post-card", Collections.singleton("postCard"), ctx);

        assertThat(html).contains("Công nghệ bạn đang dùng?");
        assertThat(html).contains("type=\"checkbox\"");
        assertThat(html).contains("data-allow-multiple=\"true\"");
        assertThat(html).contains("data-is-closed=\"false\"");
        assertThat(html).doesNotContain("Đã kết thúc");
    }

    @Test
    @DisplayName("Poll: post/_post-card renders closed expired poll with badge and results even if unvoted")
    void testPollClosedExpiredRender() {
        List<PollOptionResponse> options = List.of(
                new PollOptionResponse(301L, "Phương án A", 12, false),
                new PollOptionResponse(302L, "Phương án B", 8, false)
        );
        PollResponse poll = new PollResponse(
                70L, "Bình chọn đã hết hạn?", false,
                Instant.now().minusSeconds(3600), Instant.now().minusSeconds(3600),
                20, null, options
        );
        PostResponse postWithPoll = new PostResponse(
                3L, new UserSummary(1L, "u1", "U1", null, true), "Bài viết poll đóng",
                null, null, null, PostVisibility.PUBLIC, true, true,
                Collections.emptyList(), Collections.emptyList(), poll,
                0L, 0L, 0L, 0L, 0L,
                new PostViewerStateResponse(false, false, false),
                new PostCapabilitiesResponse(true, true, true, true),
                Instant.now(), Instant.now()
        );

        WebContext ctx = createWebContext();
        ctx.setVariable("post", postWithPoll);

        String html = templateEngine.process("post/_post-card", Collections.singleton("postCard"), ctx);

        assertThat(html).contains("Bình chọn đã hết hạn?");
        assertThat(html).contains("Đã kết thúc");
        assertThat(html).contains("data-is-closed=\"true\"");
        assertThat(html).contains("class=\"poll-results");
        assertThat(html).doesNotContain("class=\"poll-vote-form");
    }

    @Test
    @DisplayName("Profile: user/profile renders retry alert when postsError is true without false empty state")
    void testProfilePostsServiceFailureFixture() {
        ProfileResponse profileUser = new ProfileResponse(
                1L, "sinhvien1", "Sinh Viên 1", "Khoa CNTT",
                null, null, false, false, 10L, 5L, 2L, null
        );
        WebContext ctx = createWebContext();
        ctx.setVariable("profile", profileUser);
        ctx.setVariable("currentUser", profileUser);
        ctx.setVariable("postsError", true);
        ctx.setVariable("profilePosts", null);
        ctx.setVariable("title", profileUser.displayName());

        String html = templateEngine.process("user/profile", ctx);

        assertThat(html).contains("Không thể tải danh sách bài viết vào lúc này");
        assertThat(html).doesNotContain("Chưa có bài viết nào");
        assertThat(html).doesNotContain("Danh sách bài viết chưa khả dụng");
    }

    @Test
    @DisplayName("Profile: user/profile renders retry alert when savedPostsError is true without false empty state")
    void testProfileSavedPostsServiceFailureFixture() {
        ProfileResponse profileUser = new ProfileResponse(
                1L, "sinhvien1", "Sinh Viên 1", "Khoa CNTT",
                null, null, false, false, 10L, 5L, 2L, null
        );
        WebContext ctx = createWebContext();
        ctx.setVariable("profile", profileUser);
        ctx.setVariable("currentUser", profileUser);
        ctx.setVariable("savedPostsError", true);
        ctx.setVariable("savedPosts", null);
        ctx.setVariable("title", profileUser.displayName());

        String html = templateEngine.process("user/profile", ctx);

        assertThat(html).contains("Không thể tải danh sách bài viết đã lưu vào lúc này");
        assertThat(html).doesNotContain("Chưa có bài viết đã lưu nào");
        assertThat(html).doesNotContain("Danh sách bài viết đã lưu chưa khả dụng");
    }

    @Test
    @DisplayName("MockMvc GET /users/{username} gracefully degrades with HTTP 200 and postsError alert on post service failure")
    void testMockMvcProfilePostServiceFailure() throws Exception {
        ProfileResponse profile = new ProfileResponse(
                99L, "error_user", "Error User", "Khoa CNTT",
                null, null, false, false, 0L, 0L, 0L, null
        );
        when(userService.getProfileByUsername("error_user")).thenReturn(profile);
        when(postService.userPosts(eq(99L), anyInt(), anyInt(), any())).thenThrow(new RuntimeException("Post service down"));

        mockMvc.perform(get("/users/error_user")
                        .with(authentication(createMockAuth(1L, "viewer"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Không thể tải danh sách bài viết vào lúc này")));
    }

    @Test
    @DisplayName("Batch 1 Scope A: Sidebar contains strictly Feed, Profile, Communities, Friends, Saved Posts and excludes Search, Messages, Settings, Create Post, Notifications")
    void testSidebarStrictScope() {
        WebContext ctx = createWebContext();
        ctx.setVariable("activeNav", "feed");
        ctx.setVariable("currentUser", new UserSummary(1L, "testuser", "Test User", null, true));

        String html = templateEngine.process("fragments/sidebar", Collections.singleton("sidebar"), ctx);

        // Required entries present
        assertThat(html).contains("href=\"/feed\"");
        assertThat(html).contains("href=\"/communities\"");
        assertThat(html).contains("href=\"/friends\"");
        assertThat(html).contains("href=\"/saved\"");
        assertThat(html).contains("href=\"/users/testuser\"");

        // Excluded entries absent
        assertThat(html).doesNotContain("href=\"/messages\"");
        assertThat(html).doesNotContain("href=\"/search\"");
        assertThat(html).doesNotContain("href=\"/settings\"");
        assertThat(html).doesNotContain("href=\"/notifications\"");
        assertThat(html).doesNotContain("data-action=\"navigate-create-post\"");
        assertThat(html).doesNotContain("data-bs-target=\"#createPostModal\"");
    }

    @Test
    @DisplayName("Batch 1 Scope B: Modal contains createPostModal when activeNav != create-post and is suppressed when activeNav == create-post")
    void testCreatePostModalActiveNavGuard() {
        WebContext ctxFeed = createWebContext();
        ctxFeed.setVariable("activeNav", "feed");
        String htmlFeed = templateEngine.process("fragments/modal", Collections.singleton("postModals"), ctxFeed);
        assertThat(htmlFeed).contains("id=\"createPostModal\"");
        assertThat(htmlFeed).contains("id=\"create-post-form\"");
        assertThat(htmlFeed).contains("id=\"post-community-id\"");
        assertThat(htmlFeed).contains("id=\"btn-create-post\"");

        WebContext ctxCreate = createWebContext();
        ctxCreate.setVariable("activeNav", "create-post");
        String htmlCreate = templateEngine.process("fragments/modal", Collections.singleton("postModals"), ctxCreate);
        assertThat(htmlCreate).doesNotContain("id=\"createPostModal\"");
    }

    @Test
    @DisplayName("Batch 1 Scope C: Navbar renders compact recent notification preview container and actions")
    void testNavbarNotificationPreviewStructure() {
        WebContext ctx = createWebContext();
        ctx.setVariable("currentUser", new UserSummary(1L, "testuser", "Test User", null, true));

        String html = templateEngine.process("fragments/navbar", Collections.singleton("navbar"), ctx);

        assertThat(html).contains("id=\"navbarNotificationDropdown\"");
        assertThat(html).contains("id=\"navbar-notification-preview\"");
        assertThat(html).contains("id=\"navbar-notification-list\"");
        assertThat(html).contains("id=\"btn-navbar-mark-all-read\"");
        assertThat(html).contains("id=\"navbar-notifications-view-all\"");
        assertThat(html).contains("href=\"/notifications\"");
    }

    @Test
    @DisplayName("Batch 1 Scope C (QA P1): Verify exact Jackson serialization of NotificationResponse boolean isRead")
    void testNotificationResponseJacksonSerialization() throws Exception {
        NotificationResponse readNotif = NotificationResponse.builder()
                .id(1L)
                .isRead(true)
                .build();
        String jsonRead = objectMapper.writeValueAsString(readNotif);
        System.out.println("DEBUG_JSON_READ: " + jsonRead);

        NotificationResponse unreadNotif = NotificationResponse.builder()
                .id(2L)
                .isRead(false)
                .build();
        String jsonUnread = objectMapper.writeValueAsString(unreadNotif);
        System.out.println("DEBUG_JSON_UNREAD: " + jsonUnread);

        // Verification of real Jackson serialization keys:
        // Lombok generates isRead() getter for boolean isRead; Jackson discovers property name as "read".
        assertThat(jsonRead).contains("\"read\":true");
        assertThat(jsonUnread).contains("\"read\":false");
        assertThat(jsonRead).doesNotContain("\"isRead\"");
    }

    @Test
    @DisplayName("Phase B & Round 2 Polish: Navbar suppresses Create Post only on feed (isFeedPage == true), and renders on other pages and post-detail aliases where activeNav == 'feed' but isFeedPage is absent")
    void testPhaseBNavbarAndFeedPolish() {
        // 1. On feed page (isFeedPage == true), navbar suppresses Create Post buttons
        WebContext ctxFeed = createWebContext();
        ctxFeed.setVariable("activeNav", "feed");
        ctxFeed.setVariable("isFeedPage", true);
        ctxFeed.setVariable("currentUser", new UserSummary(1L, "testuser", "Test User", null, true));
        String htmlNavFeed = templateEngine.process("fragments/navbar", Collections.singleton("navbar"), ctxFeed);
        assertThat(htmlNavFeed).doesNotContain("data-action=\"navigate-create-post\"");

        // 2. On other pages (e.g. activeNav == 'communities'), navbar renders Create Post buttons
        WebContext ctxComm = createWebContext();
        ctxComm.setVariable("activeNav", "communities");
        ctxComm.setVariable("currentUser", new UserSummary(1L, "testuser", "Test User", null, true));
        String htmlNavComm = templateEngine.process("fragments/navbar", Collections.singleton("navbar"), ctxComm);
        assertThat(htmlNavComm).contains("data-action=\"navigate-create-post\"");
        assertThat(htmlNavComm).contains("Tạo bài viết");

        // 3. Post-detail regression: PostPageController sets activeNav = 'feed' on /post/{id}, /posts/{id}, /p/{id}
        // but isFeedPage is absent (null). Navbar MUST render Create Post buttons for both desktop and mobile.
        WebContext ctxPostDetail = createWebContext();
        ctxPostDetail.setVariable("activeNav", "feed");
        // isFeedPage is null/absent
        ctxPostDetail.setVariable("currentUser", new UserSummary(1L, "testuser", "Test User", null, true));
        String htmlNavPostDetail = templateEngine.process("fragments/navbar", Collections.singleton("navbar"), ctxPostDetail);
        assertThat(htmlNavPostDetail).contains("data-action=\"navigate-create-post\"");
        assertThat(htmlNavPostDetail).contains("Tạo bài viết");
        assertThat(htmlNavPostDetail).contains("aloute-btn-icon-mobile");

        // 4. Missing-post error state: activeNav = 'feed', isFeedPage absent -> navbar retains Create Post
        WebContext ctxMissingPost = createWebContext();
        ctxMissingPost.setVariable("activeNav", "feed");
        ctxMissingPost.setVariable("currentUser", new UserSummary(1L, "testuser", "Test User", null, true));
        ctxMissingPost.setVariable("postError", true);
        String htmlNavMissingPost = templateEngine.process("fragments/navbar", Collections.singleton("navbar"), ctxMissingPost);
        assertThat(htmlNavMissingPost).contains("data-action=\"navigate-create-post\"");
        assertThat(htmlNavMissingPost).contains("Tạo bài viết");
    }

    @Test
    @DisplayName("PostCard: post/_post-card renders IMAGE, GIF, and VIDEO media elements with escaped src and controls")
    void testPostCardMediaBranchesRender() {
        MediaResponse imageMedia = new MediaResponse(1L, MediaType.IMAGE, "https://storage.ute.udn.vn/media/img1.jpg?w=800&h=600", 800, 600, null);
        MediaResponse gifMedia = new MediaResponse(2L, MediaType.GIF, "https://storage.ute.udn.vn/media/anim.gif?loop=1&speed=normal", 400, 300, null);
        MediaResponse videoMedia = new MediaResponse(3L, MediaType.VIDEO, "https://storage.ute.udn.vn/media/clip.mp4?format=mp4&res=1080", 1920, 1080, 15.5);
        MediaResponse nullTypeMedia = new MediaResponse(4L, null, "https://storage.ute.udn.vn/media/legacy.png?fallback=1&type=null", 640, 480, null);

        PostResponse postWithAllMedia = new PostResponse(
                88L, new UserSummary(1L, "mediauser", "Media User", null, true), "Post with media",
                null, null, null, PostVisibility.PUBLIC, true, true,
                List.of(imageMedia, gifMedia, videoMedia, nullTypeMedia), Collections.emptyList(), null,
                0L, 0L, 0L, 0L, 0L,
                new PostViewerStateResponse(false, false, false),
                new PostCapabilitiesResponse(true, true, true, true),
                Instant.now(), Instant.now()
        );

        WebContext ctx = createWebContext();
        ctx.setVariable("post", postWithAllMedia);

        String html = templateEngine.process("post/_post-card", Collections.singleton("postCard"), ctx);

        // 1. IMAGE: rendered as <img> with escaped src containing &amp;
        assertThat(html).contains("src=\"https://storage.ute.udn.vn/media/img1.jpg?w=800&amp;h=600\"");
        assertThat(html).contains("alt=\"Hình ảnh đính kèm bài viết\"");

        // 2. GIF: rendered as <img> with escaped src containing &amp;
        assertThat(html).contains("src=\"https://storage.ute.udn.vn/media/anim.gif?loop=1&amp;speed=normal\"");
        assertThat(html).contains("alt=\"Ảnh GIF đính kèm bài viết\"");

        // 3. VIDEO: rendered as <video> with controls, preload="metadata", and escaped src containing &amp;
        assertThat(html).contains("<video");
        assertThat(html).contains("src=\"https://storage.ute.udn.vn/media/clip.mp4?format=mp4&amp;res=1080\"");
        assertThat(html).contains("controls");
        assertThat(html).contains("preload=\"metadata\"");
        assertThat(html).contains("Trình duyệt của bạn không hỗ trợ thẻ video.");

        // 4. Null type: falls back to <img> with escaped src containing &amp;
        assertThat(html).contains("src=\"https://storage.ute.udn.vn/media/legacy.png?fallback=1&amp;type=null\"");
    }


    @Test
    @DisplayName("Community UI Cleanup: detail view renders correct buttons for Guest, Non-member, Member, Owner, and clean empty state")
    void testCommunityDetailUiCleanupAndRoles() {
        // 1. Guest: sees "Đăng nhập để tham gia", "Tất cả", no "Đăng bài", no "Rời cộng đồng", no "Tham gia"
        WebContext ctxGuest = createWebContext();
        CommunityResponse commGuest = new CommunityResponse(
                10L, 1L, "Cộng đồng AI", "ai-comm", "Mô tả", null, null, false, 50L, 0L, null, false
        );
        ctxGuest.setVariable("community", commGuest);
        ctxGuest.setVariable("posts", Collections.emptyList());
        ctxGuest.setVariable("currentUser", null);

        String htmlGuest = templateEngine.process("community/detail", ctxGuest);
        assertThat(htmlGuest).contains("data-is-private=\"false\"");
        assertThat(htmlGuest).contains("Tất cả");
        assertThat(htmlGuest).contains("Đăng nhập để tham gia");
        assertThat(htmlGuest).doesNotContain("id=\"btn-community-post\"");
        assertThat(htmlGuest).doesNotContain("id=\"btn-leave-community\"");
        assertThat(htmlGuest).doesNotContain("id=\"btn-join-community\"");
        assertThat(htmlGuest).doesNotContain("Trưởng nhóm");
        // Verify empty state has no redundant "Tạo bài viết ngay" button
        assertThat(htmlGuest).contains("Chưa có bài viết nào");
        assertThat(htmlGuest).doesNotContain("Tạo bài viết ngay");

        // 2. Non-member: sees "Tham gia" (#btn-join-community), #btn-community-post with d-none, no "Rời cộng đồng"
        WebContext ctxNonMember = createWebContext();
        ProfileResponse userNonMember = new ProfileResponse(
                20L, "nonmember", "Non Member", "bio", null, null, false, false, 0, 0, 0, null
        );
        CommunityResponse commNonMember = new CommunityResponse(
                10L, 1L, "Cộng đồng AI", "ai-comm", "Mô tả", null, null, false, 50L, 0L, null, false
        );
        ctxNonMember.setVariable("community", commNonMember);
        ctxNonMember.setVariable("posts", Collections.emptyList());
        ctxNonMember.setVariable("currentUser", userNonMember);

        String htmlNonMember = templateEngine.process("community/detail", ctxNonMember);
        assertThat(htmlNonMember).contains("id=\"btn-join-community\"");
        assertThat(htmlNonMember).contains("Tham gia");
        assertThat(htmlNonMember).contains("id=\"btn-community-post\"");
        assertThat(htmlNonMember).contains("btn-primary rounded-pill d-none");
        assertThat(htmlNonMember).doesNotContain("id=\"btn-leave-community\"");
        assertThat(htmlNonMember).doesNotContain("Trưởng nhóm");
        assertThat(htmlNonMember).doesNotContain("Đăng nhập để tham gia");

        // 3. Member: sees "Rời cộng đồng" (#btn-leave-community), visible "Đăng bài" (#btn-community-post), no "Tham gia"
        WebContext ctxMember = createWebContext();
        ProfileResponse userMember = new ProfileResponse(
                21L, "memberuser", "Member User", "bio", null, null, false, false, 0, 0, 0, null
        );
        CommunityResponse commMember = new CommunityResponse(
                10L, 1L, "Cộng đồng AI", "ai-comm", "Mô tả", null, null, false, 51L, 0L, CommunityRole.MEMBER, false
        );
        ctxMember.setVariable("community", commMember);
        ctxMember.setVariable("posts", Collections.emptyList());
        ctxMember.setVariable("currentUser", userMember);

        String htmlMember = templateEngine.process("community/detail", ctxMember);
        assertThat(htmlMember).contains("id=\"btn-leave-community\"");
        assertThat(htmlMember).contains("Rời cộng đồng");
        assertThat(htmlMember).contains("id=\"btn-community-post\"");
        assertThat(htmlMember).contains("btn btn-sm btn-primary rounded-pill");
        assertThat(htmlMember).doesNotContain("btn-primary rounded-pill d-none");
        assertThat(htmlMember).doesNotContain("id=\"btn-join-community\"");
        assertThat(htmlMember).doesNotContain("Trưởng nhóm");

        // 4. Owner with membership: sees "Trưởng nhóm" badge, visible "Đăng bài", no "Rời cộng đồng", no "Tham gia"
        WebContext ctxOwner = createWebContext();
        ProfileResponse userOwner = new ProfileResponse(
                1L, "owneruser", "Owner User", "bio", null, null, false, false, 0, 0, 0, null
        );
        CommunityResponse commOwner = new CommunityResponse(
                10L, 1L, "Cộng đồng AI", "ai-comm", "Mô tả", null, null, false, 51L, 0L, CommunityRole.ADMIN, true
        );
        ctxOwner.setVariable("community", commOwner);
        ctxOwner.setVariable("posts", Collections.emptyList());
        ctxOwner.setVariable("currentUser", userOwner);

        String htmlOwner = templateEngine.process("community/detail", ctxOwner);
        assertThat(htmlOwner).contains("Trưởng nhóm");
        assertThat(htmlOwner).contains("id=\"btn-community-post\"");
        assertThat(htmlOwner).doesNotContain("btn-primary rounded-pill d-none");
        assertThat(htmlOwner).doesNotContain("id=\"btn-leave-community\"");
        assertThat(htmlOwner).doesNotContain("id=\"btn-join-community\"");

        // 5. Owner WITHOUT membership regression: sees "Trưởng nhóm" badge, but "Đăng bài" is gated with d-none
        WebContext ctxOwnerNoMember = createWebContext();
        CommunityResponse commOwnerNoMember = new CommunityResponse(
                10L, 1L, "Cộng đồng AI", "ai-comm", "Mô tả", null, null, false, 50L, 0L, null, true
        );
        ctxOwnerNoMember.setVariable("community", commOwnerNoMember);
        ctxOwnerNoMember.setVariable("posts", Collections.emptyList());
        ctxOwnerNoMember.setVariable("currentUser", userOwner);

        String htmlOwnerNoMember = templateEngine.process("community/detail", ctxOwnerNoMember);
        assertThat(htmlOwnerNoMember).contains("Trưởng nhóm");
        assertThat(htmlOwnerNoMember).contains("id=\"btn-community-post\"");
        assertThat(htmlOwnerNoMember).contains("btn-primary rounded-pill d-none");
        assertThat(htmlOwnerNoMember).doesNotContain("id=\"btn-leave-community\"");
        assertThat(htmlOwnerNoMember).doesNotContain("id=\"btn-join-community\"");
    }

    @Test
    @DisplayName("Standalone Create Post: binds communityId to hidden input in composer form")
    void testStandaloneCreatePostBindsCommunityId() {
        WebContext ctx = createWebContext();
        ctx.setVariable("communityId", 42L);
        ctx.setVariable("currentUser", new ProfileResponse(1L, "user1", "User One", null, null, null, false, false, 0, 0, 0, null));

        String html = templateEngine.process("post/create", ctx);
        assertThat(html).contains("id=\"post-community-id\"");
        assertThat(html).contains("value=\"42\"");
    }

    @Test
    @DisplayName("Community UI Cleanup: Navbar suppresses Create Post when hideGlobalCreatePost or isCommunityDetailPage is true")
    void testCommunityDetailNavbarSuppressed() {
        WebContext ctx = createWebContext();
        ctx.setVariable("activeNav", "communities");
        ctx.setVariable("hideGlobalCreatePost", true);
        ctx.setVariable("isCommunityDetailPage", true);
        ctx.setVariable("currentUser", new UserSummary(1L, "testuser", "Test User", null, true));

        String html = templateEngine.process("fragments/navbar", Collections.singleton("navbar"), ctx);
        assertThat(html).doesNotContain("data-action=\"navigate-create-post\"");
        assertThat(html).doesNotContain("Tạo bài viết");
    }
}