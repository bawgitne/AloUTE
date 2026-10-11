package com.aloute.controller.page;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.dto.response.*;
import com.aloute.entity.CommunityRole;
import com.aloute.entity.PostVisibility;
import com.aloute.entity.User;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.CommunityService;
import com.aloute.service.PostService;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @MockBean
    private CommunityService communityService;

    @MockBean
    private PostService postService;

    @MockBean
    private UserService userService;

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
        assertThat(html).contains("/communities/42");
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
    @DisplayName("Export rendered HTML fixtures for Community, Messaging, and Error views into bridge job folder")
    void exportRenderedFixtures() throws Exception {
        Path fixturesDir = Paths.get(".antigravity-bridge", "jobs", "2026-10-11-002", "fixtures");
        Files.createDirectories(fixturesDir);

        ProfileResponse currentUser = new ProfileResponse(
                1L, "nguyen_van_a", "Nguyễn Văn A", "Khoa Công nghệ Thông tin",
                "/src/main/resources/static/images/avatar-fallback.svg",
                "https://aloute.ute.udn.vn", false, false, 45L, 28L, 12L, null
        );

        CommunityResponse comm1 = new CommunityResponse(
                1L, 1L, "CLB Tin học AloUTE", "clb-tin-hoc",
                "Cộng đồng chia sẻ kiến thức lập trình và công nghệ sinh viên UTE.",
                "/src/main/resources/static/images/avatar-fallback.svg",
                null, false, 250L, 42L, CommunityRole.MEMBER, false
        );
        CommunityResponse comm2 = new CommunityResponse(
                2L, 2L, "CLB Tiếng Anh UTE", "clb-tieng-anh",
                "Môi trường rèn luyện kỹ năng giao tiếp tiếng Anh cho sinh viên.",
                "/src/main/resources/static/images/avatar-fallback.svg",
                null, false, 180L, 20L, null, false
        );

        // 1. Community Index Populated
        WebContext ctxCommPop = createWebContext();
        ctxCommPop.setVariable("communities", List.of(comm1, comm2));
        ctxCommPop.setVariable("currentUser", currentUser);
        ctxCommPop.setVariable("title", "Cộng đồng & Câu lạc bộ");
        String htmlCommPop = rewriteAssetPaths(templateEngine.process("community/index", ctxCommPop));
        Files.writeString(fixturesDir.resolve("community-populated.html"), htmlCommPop, StandardCharsets.UTF_8);

        // 2. Community Index Empty
        WebContext ctxCommEmpty = createWebContext();
        ctxCommEmpty.setVariable("communities", Collections.emptyList());
        ctxCommEmpty.setVariable("currentUser", currentUser);
        ctxCommEmpty.setVariable("title", "Cộng đồng & Câu lạc bộ");
        String htmlCommEmpty = rewriteAssetPaths(templateEngine.process("community/index", ctxCommEmpty));
        Files.writeString(fixturesDir.resolve("community-empty.html"), htmlCommEmpty, StandardCharsets.UTF_8);

        // 3. Community Detail Populated
        PostResponse post1 = createSamplePost(101L, "Chào mừng các bạn tân sinh viên đến với CLB Tin học AloUTE!", Instant.now());
        PostResponse post2 = createSamplePost(102L, "Lịch sinh hoạt chuyên đề tuần tới sẽ diễn ra tại phòng máy A1.", Instant.now());
        WebContext ctxDetailPop = createWebContext();
        ctxDetailPop.setVariable("community", comm1);
        ctxDetailPop.setVariable("posts", List.of(post1, post2));
        ctxDetailPop.setVariable("currentUser", currentUser);
        ctxDetailPop.setVariable("title", comm1.name());
        String htmlDetailPop = rewriteAssetPaths(templateEngine.process("community/detail", ctxDetailPop));
        Files.writeString(fixturesDir.resolve("community-detail-populated.html"), htmlDetailPop, StandardCharsets.UTF_8);

        // 4. Community Detail Empty
        WebContext ctxDetailEmpty = createWebContext();
        ctxDetailEmpty.setVariable("community", comm1);
        ctxDetailEmpty.setVariable("posts", Collections.emptyList());
        ctxDetailEmpty.setVariable("currentUser", currentUser);
        ctxDetailEmpty.setVariable("title", comm1.name());
        String htmlDetailEmpty = rewriteAssetPaths(templateEngine.process("community/detail", ctxDetailEmpty));
        Files.writeString(fixturesDir.resolve("community-detail-empty.html"), htmlDetailEmpty, StandardCharsets.UTF_8);

        // 5. Messaging Populated
        WebContext ctxMsgPop = createWebContext();
        ctxMsgPop.setVariable("currentUser", currentUser);
        ctxMsgPop.setVariable("currentConversation", null);
        ctxMsgPop.setVariable("title", "Tin nhắn — AloUTE");
        String htmlMsgPop = rewriteAssetPaths(templateEngine.process("messaging/index", ctxMsgPop));
        String mockTransportPop = """
            <script>
            /* FIXTURE SMOKE ONLY: Test-only deterministic mock transport for offline browser rendering */
            (function() {
                const originalFetch = window.fetch;
                window.fetch = async function(url, options) {
                    if (typeof url === 'string') {
                        if (url.includes('/api/conversations') && url.includes('/messages')) {
                            return new Response(JSON.stringify({
                                success: true,
                                data: [
                                    { id: 101, content: 'Chào bạn! Bạn có tài liệu môn Lập trình Web không?', createdAt: '2026-10-11T10:00:00Z', sender: { id: 2, displayName: 'Trần Thị B', username: 'tranthib' } },
                                    { id: 102, content: 'Chào B, mình có slide bài giảng và bài tập thực hành nhé!', createdAt: '2026-10-11T10:02:00Z', sender: { id: 1, displayName: 'Nguyễn Văn A', username: 'nguyen_van_a' } }
                                ],
                                meta: { page: 0, hasNext: false }
                            }), { status: 200, headers: { 'Content-Type': 'application/json' } });
                        }
                        if (url.includes('/api/conversations/1')) {
                            return new Response(JSON.stringify({
                                success: true,
                                data: { id: 1, name: 'Trần Thị B', otherParticipant: { id: 2, displayName: 'Trần Thị B', username: 'tranthib' } }
                            }), { status: 200, headers: { 'Content-Type': 'application/json' } });
                        }
                        if (url.includes('/api/conversations')) {
                            return new Response(JSON.stringify({
                                success: true,
                                data: [
                                    { id: 1, name: 'Trần Thị B', otherParticipant: { id: 2, displayName: 'Trần Thị B', username: 'tranthib' }, unreadCount: 1, lastMessage: { content: 'Chào B, mình có slide...', createdAt: '2026-10-11T10:02:00Z' } },
                                    { id: 2, name: 'CLB Tin học AloUTE', members: [{ id: 1 }, { id: 3, username: 'admin' }], unreadCount: 0, lastMessage: null }
                                ],
                                meta: { page: 0, hasNext: false }
                            }), { status: 200, headers: { 'Content-Type': 'application/json' } });
                        }
                    }
                    return originalFetch ? originalFetch.apply(this, arguments) : Promise.reject(new Error('Mock unhandled'));
                };
            })();
            </script>
            """;
        htmlMsgPop = htmlMsgPop.replace("</body>", mockTransportPop + "\n</body>");
        Files.writeString(fixturesDir.resolve("messaging-populated.html"), htmlMsgPop, StandardCharsets.UTF_8);

        // 6. Messaging Empty
        WebContext ctxMsgEmpty = createWebContext();
        ctxMsgEmpty.setVariable("currentUser", currentUser);
        ctxMsgEmpty.setVariable("currentConversation", null);
        ctxMsgEmpty.setVariable("title", "Tin nhắn — AloUTE");
        String htmlMsgEmpty = rewriteAssetPaths(templateEngine.process("messaging/index", ctxMsgEmpty));
        String mockTransportEmpty = """
            <script>
            /* FIXTURE SMOKE ONLY: Test-only deterministic empty mock transport */
            (function() {
                window.fetch = async function(url) {
                    return new Response(JSON.stringify({
                        success: true,
                        data: [],
                        meta: { page: 0, hasNext: false }
                    }), { status: 200, headers: { 'Content-Type': 'application/json' } });
                };
            })();
            </script>
            """;
        htmlMsgEmpty = htmlMsgEmpty.replace("</body>", mockTransportEmpty + "\n</body>");
        Files.writeString(fixturesDir.resolve("messaging-empty.html"), htmlMsgEmpty, StandardCharsets.UTF_8);

        // 7. Error 404
        WebContext ctx404 = createWebContext();
        ctx404.setVariable("path", "/communities/khong-ton-tai");
        ctx404.setVariable("currentUser", null);
        String html404 = rewriteAssetPaths(templateEngine.process("error/404", ctx404));
        Files.writeString(fixturesDir.resolve("error-404.html"), html404, StandardCharsets.UTF_8);

        // 8. Error 500
        WebContext ctx500 = createWebContext();
        ctx500.setVariable("currentUser", null);
        String html500 = rewriteAssetPaths(templateEngine.process("error/500", ctx500));
        Files.writeString(fixturesDir.resolve("error-500.html"), html500, StandardCharsets.UTF_8);

        assertThat(Files.exists(fixturesDir.resolve("community-populated.html"))).isTrue();
        assertThat(Files.exists(fixturesDir.resolve("messaging-populated.html"))).isTrue();
    }

    private String rewriteAssetPaths(String html) {
        return html
                .replace("href=\"/css/", "href=\"/src/main/resources/static/css/")
                .replace("src=\"/css/", "src=\"/src/main/resources/static/css/")
                .replace("src=\"/js/", "src=\"/src/main/resources/static/js/")
                .replace("href=\"/js/", "href=\"/src/main/resources/static/js/")
                .replace("src=\"/images/", "src=\"/src/main/resources/static/images/")
                .replace("href=\"/images/", "href=\"/src/main/resources/static/images/");
    }
}

