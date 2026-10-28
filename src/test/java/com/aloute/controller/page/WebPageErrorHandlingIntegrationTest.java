package com.aloute.controller.page;

import com.aloute.entity.User;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-22 regression suite: web page error handling must be content-negotiated.
 *
 * <ul>
 *   <li>Browser navigation ({@code Accept: text/html}): HTML error pages or a
 *       login redirect — never a raw JSON envelope on a page.</li>
 *   <li>API / JSON callers: canonical {@code ApiResponse} JSON envelope.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class WebPageErrorHandlingIntegrationTest {

    private static final String BROWSER_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    @Autowired
    private MockMvc mockMvc;

    private Authentication userAuth() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 2L);
        user.setUsername("normal_user");
        user.setEmail("normal_user@aloute.local");
        user.setPasswordHash("hash1234");
        user.setActive(true);
        user.setRole(Role.USER);
        UserPrincipal principal = UserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    // ---- AUTH-15: guest navigating to a protected page -> login redirect -----------------

    @Test
    @DisplayName("AUTH-15: guest GET /post/create (HTML) redirects to /login instead of JSON")
    void guestHtmlPostCreateRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/post/create").header("Accept", BROWSER_ACCEPT))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ---- AUTH-16: guest navigating to an admin page -> login redirect --------------------

    @Test
    @DisplayName("AUTH-16: guest GET /admin (HTML) redirects to /login instead of JSON")
    void guestHtmlAdminRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin").header("Accept", BROWSER_ACCEPT))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ---- RES-02: unknown HTML route -> branded 404 HTML ---------------------------------

    @Test
    @DisplayName("RES-02: unknown route (HTML, authenticated) renders 404 HTML with feed link")
    void unknownHtmlRouteRenders404Page() throws Exception {
        mockMvc.perform(get("/khong-ton-tai-xyz")
                        .header("Accept", BROWSER_ACCEPT)
                        .with(authentication(userAuth())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("error-content")))
                .andExpect(content().string(containsString("404")))
                .andExpect(content().string(containsString("href=\"/feed\"")));
    }

    // ---- ADM-05: authenticated non-admin on /admin -> HTML 403 page ---------------------

    @Test
    @DisplayName("ADM-05: authenticated non-admin GET /admin (HTML) is denied and forwarded to the HTML error page")
    void nonAdminHtmlAdminRenders403Page() throws Exception {
        mockMvc.perform(get("/admin")
                        .header("Accept", BROWSER_ACCEPT)
                        .with(authentication(userAuth())))
                .andExpect(status().isForbidden())
                // The security AccessDeniedHandler forwards to /error, which renders
                // error/403 (verified by errorPage403RendersHtml below).
                .andExpect(forwardedUrl("/error"));
    }

    @Test
    @DisplayName("ADM-05: the 403 error page renders branded HTML (via the error dispatcher)")
    void errorPage403RendersHtml() throws Exception {
        mockMvc.perform(get("/error")
                        .header("Accept", BROWSER_ACCEPT)
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 403))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("error-content")))
                .andExpect(content().string(containsString("403")));
    }

    // ---- API contract preserved: canonical JSON envelope ---------------------------------

    @Test
    @DisplayName("API: unauthenticated request keeps the JSON envelope (401)")
    void apiUnauthenticatedKeepsJsonEnvelope() throws Exception {
        mockMvc.perform(get("/api/admin/overview").header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("API: authenticated non-admin request keeps the JSON envelope (403)")
    void apiAccessDeniedKeepsJsonEnvelope() throws Exception {
        mockMvc.perform(get("/api/admin/overview")
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                        .with(authentication(userAuth())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("API: unknown API route keeps the JSON envelope (404)")
    void unknownApiRouteKeepsJsonEnvelope() throws Exception {
        mockMvc.perform(get("/api/khong-ton-tai")
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                        .with(authentication(userAuth())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // ---- Accept header variants: fetch/AJAX (accepts */* or JSON) must not get HTML ------

    @Test
    @DisplayName("Accept */* on a protected page is treated as an API caller (JSON 401)")
    void jsonDefaultAcceptOnProtectedPageStaysJson() throws Exception {
        mockMvc.perform(get("/admin").header("Accept", "*/*"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Accept application/json on a protected page keeps the JSON envelope")
    void jsonAcceptOnProtectedPageStaysJson() throws Exception {
        mockMvc.perform(get("/admin").header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ---- Existing public pages still render --------------------------------------------

    @Test
    @DisplayName("Public page /login still renders HTML 200 for guests")
    void publicLoginPageStillRenders() throws Exception {
        mockMvc.perform(get("/login").header("Accept", BROWSER_ACCEPT))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    // ---- Admin access still works for a system admin -----------------------------------

    @Test
    @DisplayName("System admin GET /admin (HTML) reaches the page (no error redirect)")
    void systemAdminReachesAdminPage() throws Exception {
        User admin = new User();
        ReflectionTestUtils.setField(admin, "id", 1L);
        admin.setUsername("sys_admin");
        admin.setEmail("sys_admin@aloute.local");
        admin.setPasswordHash("hash1234");
        admin.setActive(true);
        admin.setRole(Role.SYSTEM_ADMIN);
        UserPrincipal principal = UserPrincipal.from(admin);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        mockMvc.perform(get("/admin").header("Accept", BROWSER_ACCEPT).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}