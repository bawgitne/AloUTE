package com.aloute.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Classifies an inbound request as an API/JSON request or a browser HTML page
 * navigation so error handling can pick the right representation.
 *
 * <p>BL-22: {@code /api/**} and {@code /ws/**} must always receive the canonical
 * JSON {@code ApiResponse} error envelope, while browser page navigation must
 * receive HTML (or a login redirect). A request is treated as HTML only when it
 * is a non-API route AND the client explicitly advertises {@code text/html}
 * without preferring {@code application/json}. Declared-JSON callers (fetch with
 * {@code Accept: application/json}, or a bare {@code *&#47;*}) keep the JSON path.
 */
public final class RequestTypeSupport {

    private RequestTypeSupport() {
    }

    public static boolean isApiRequest(HttpServletRequest request) {
        String uri = request != null ? request.getRequestURI() : null;
        if (uri == null) {
            return false;
        }
        return uri.equals("/api") || uri.startsWith("/api/")
                || uri.equals("/ws") || uri.startsWith("/ws/");
    }

    /**
     * {@code true} only for browser page navigation: non-API route, Accept header
     * present and containing {@code text/html}, and not preferring JSON.
     */
    public static boolean isHtmlPageRequest(HttpServletRequest request) {
        if (request == null || isApiRequest(request)) {
            return false;
        }
        String accept = request.getHeader("Accept");
        if (accept == null || accept.isBlank()) {
            return false;
        }
        String normalized = accept.toLowerCase();
        return normalized.contains("text/html") && !normalized.contains("application/json");
    }
}
