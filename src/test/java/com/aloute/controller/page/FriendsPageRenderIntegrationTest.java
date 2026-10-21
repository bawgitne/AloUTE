package com.aloute.controller.page;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.UserSummary;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.SocialService;
import com.aloute.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class FriendsPageRenderIntegrationTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SocialService socialService;

    @MockBean
    private UserService userService;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    private WebContext createWebContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockServletContext servletContext = new MockServletContext();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange webExchange = application.buildExchange(request, response);
        return new WebContext(webExchange, Locale.forLanguageTag("vi-VN"));
    }

    @Test
    @DisplayName("Friends: user/friends template renders empty state without exception for following and followers")
    void testFriendsEmptyStateRender() {
        WebContext ctx = createWebContext();
        ctx.setVariable("users", Collections.emptyList());
        ctx.setVariable("currentTab", "following");
        ctx.setVariable("currentUser", new UserSummary(1L, "testuser", "Test User", null, true));
        ctx.setVariable("activeNav", "friends");

        String html = templateEngine.process("user/friends", ctx);
        assertThat(html).contains("Chưa theo dõi người dùng nào");
        assertThat(html).contains("Bạn bè &amp; Kết nối");
        assertThat(html).contains("</html>");

        WebContext ctxFollowers = createWebContext();
        ctxFollowers.setVariable("users", Collections.emptyList());
        ctxFollowers.setVariable("currentTab", "followers");
        ctxFollowers.setVariable("currentUser", new UserSummary(1L, "testuser", "Test User", null, true));
        ctxFollowers.setVariable("activeNav", "friends");

        String htmlFollowers = templateEngine.process("user/friends", ctxFollowers);
        assertThat(htmlFollowers).contains("Chưa có người theo dõi");
        assertThat(htmlFollowers).contains("</html>");
    }

    @Test
    @DisplayName("Friends: user/friends template renders populated following and followers with real UserSummary without EL1008E bio exception")
    void testFriendsPopulatedStateRender() {
        UserSummary user1 = new UserSummary(10L, "sinhvien_ute", "Nguyễn Văn Sinh Viên", "/images/avatar1.svg", true);
        UserSummary user2 = new UserSummary(11L, "tran_thi_b", "Trần Thị B", null, false);

        WebContext ctx = createWebContext();
        ctx.setVariable("users", List.of(user1, user2));
        ctx.setVariable("currentTab", "following");
        ctx.setVariable("currentUser", new UserSummary(1L, "testuser", "Test User", null, true));
        ctx.setVariable("activeNav", "friends");

        // On baseline with u.bio, this call throws TemplateInputException / EL1008E
        String html = templateEngine.process("user/friends", ctx);

        assertThat(html).contains("Nguyễn Văn Sinh Viên");
        assertThat(html).contains("@sinhvien_ute");
        assertThat(html).contains("/users/sinhvien_ute");
        assertThat(html).contains("Trần Thị B");
        assertThat(html).contains("@tran_thi_b");
        assertThat(html).contains("/users/tran_thi_b");
        assertThat(html).contains("Xem hồ sơ");
        assertThat(html).contains("</html>");
    }

    @Test
    @WithMockUser(username = "current_user")
    @DisplayName("Friends Controller: GET /friends end-to-end renders populated following and followers with HTTP 200 OK")
    void testFriendsControllerEndToEnd() throws Exception {
        Long currentUserId = 1L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(currentUserId));

        UserSummary followingUser = new UserSummary(20L, "following_user", "Following User", "/images/avatar20.svg", true);
        when(socialService.following(eq(currentUserId), any(Pageable.class)))
                .thenReturn(ApiResponse.success(List.of(followingUser)));

        mockMvc.perform(get("/friends").param("tab", "following"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Following User")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("@following_user")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/users/following_user")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("</html>")));

        UserSummary followerUser = new UserSummary(21L, "follower_user", "Follower User", null, false);
        when(socialService.followers(eq(currentUserId), any(Pageable.class)))
                .thenReturn(ApiResponse.success(List.of(followerUser)));

        mockMvc.perform(get("/friends").param("tab", "followers"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Follower User")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("@follower_user")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/users/follower_user")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("</html>")));
    }
}
