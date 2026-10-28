package com.aloute.common.exception;

import com.aloute.common.web.RequestTypeSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BL-22: the global exception handler must render HTML error views for browser
 * navigation and keep the JSON envelope for API/JSON callers, without ever
 * collapsing a genuine 5xx into a 404.
 */
class GlobalExceptionHandlerHtmlTest {

    private static final String BROWSER_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest htmlRequest(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", uri);
        r.setRequestURI(uri);
        r.addHeader("Accept", BROWSER_ACCEPT);
        r.setAttribute("traceId", "trace-html");
        return r;
    }

    private MockHttpServletRequest jsonRequest(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", uri);
        r.setRequestURI(uri);
        r.addHeader("Accept", "application/json");
        return r;
    }

    @Test
    @DisplayName("HTML: unknown route renders error/404 with 404 status")
    void htmlUnknownRouteRenders404() {
        Object result = handler.handleNoResourceFound(
                new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/nope"),
                htmlRequest("/nope"));
        assertThat(result).isInstanceOf(ModelAndView.class);
        ModelAndView mv = (ModelAndView) result;
        assertThat(mv.getViewName()).isEqualTo("error/404");
        assertThat(mv.getStatus()).isNotNull();
        assertThat(mv.getStatus().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("HTML: unauthenticated page navigation redirects to /login")
    void htmlUnauthenticatedRedirectsToLogin() {
        Object result = handler.handleAppException(
                new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHENTICATED", "Authentication required"),
                htmlRequest("/post/create"));
        assertThat(result).isInstanceOf(ModelAndView.class);
        assertThat(((ModelAndView) result).getViewName()).isEqualTo("redirect:/login");
    }

    @Test
    @DisplayName("HTML: access denied renders error/403 with 403 status")
    void htmlAccessDeniedRenders403() {
        Object result = handler.handleAccessDenied(new AccessDeniedException("denied"), htmlRequest("/admin"));
        assertThat(result).isInstanceOf(ModelAndView.class);
        ModelAndView mv = (ModelAndView) result;
        assertThat(mv.getViewName()).isEqualTo("error/403");
        assertThat(mv.getStatus().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("HTML: unexpected failure renders error/500 with real 500 status (never 404)")
    void htmlUnexpectedRenders500NotMaskedAs404() {
        Object result = handler.handleUnexpected(new RuntimeException("boom"), htmlRequest("/feed"));
        assertThat(result).isInstanceOf(ModelAndView.class);
        ModelAndView mv = (ModelAndView) result;
        assertThat(mv.getViewName()).isEqualTo("error/500");
        assertThat(mv.getStatus().value()).isEqualTo(500);
    }

    @Test
    @DisplayName("HTML: a 400 response is not disguised as a 404 page")
    void htmlBadRequestKeepsTrueStatus() {
        Object result = handler.handleMessageNotReadable(
                new org.springframework.http.converter.HttpMessageNotReadableException("bad"),
                htmlRequest("/post/create"));
        assertThat(result).isInstanceOf(ModelAndView.class);
        ModelAndView mv = (ModelAndView) result;
        assertThat(mv.getStatus().value()).isEqualTo(400);
        assertThat(mv.getViewName()).isEqualTo("error/500"); // fallback view, true status kept
    }

    @Test
    @DisplayName("JSON: application/json caller keeps the ResponseEntity envelope")
    void jsonCallerKeepsEnvelope() {
        Object result = handler.handleAppException(
                new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "Post not found"),
                jsonRequest("/api/posts/999"));
        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> re = (ResponseEntity<?>) result;
        assertThat(re.getStatusCode().value()).isEqualTo(404);
        assertThat(re.getBody()).isInstanceOf(ErrorResponse.class);
    }

    @Test
    @DisplayName("Classifier: /api and /ws are API; text/html page is HTML; */* is JSON")
    void classifierRules() {
        assertThat(RequestTypeSupport.isApiRequest(jsonRequest("/api/posts"))).isTrue();
        assertThat(RequestTypeSupport.isApiRequest(jsonRequest("/ws/chat"))).isTrue();
        assertThat(RequestTypeSupport.isHtmlPageRequest(htmlRequest("/feed"))).isTrue();
        assertThat(RequestTypeSupport.isHtmlPageRequest(jsonRequest("/feed"))).isFalse();
        assertThat(RequestTypeSupport.isHtmlPageRequest(htmlRequest("/api/posts"))).isFalse();

        MockHttpServletRequest star = new MockHttpServletRequest("GET", "/feed");
        star.setRequestURI("/feed");
        star.addHeader("Accept", "*/*");
        assertThat(RequestTypeSupport.isHtmlPageRequest(star)).isFalse();

        MockHttpServletRequest both = new MockHttpServletRequest("GET", "/feed");
        both.setRequestURI("/feed");
        both.addHeader("Accept", "text/html,application/json");
        assertThat(RequestTypeSupport.isHtmlPageRequest(both)).isFalse();
    }
}