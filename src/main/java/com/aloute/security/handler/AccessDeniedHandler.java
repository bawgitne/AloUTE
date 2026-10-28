package com.aloute.security.handler;

import com.aloute.common.exception.ApiError;
import com.aloute.common.exception.ErrorResponse;
import com.aloute.common.web.RequestTypeSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccessDeniedHandler implements org.springframework.security.web.access.AccessDeniedHandler {
    private final ObjectMapper objectMapper;
    @Override public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException, jakarta.servlet.ServletException {
        // BL-22: a browser that lacks permission gets the HTML 403 error page
        // (via the error dispatcher, deterministic in containers and tests);
        // API/JSON callers keep the canonical JSON envelope.
        if (RequestTypeSupport.isHtmlPageRequest(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            request.setAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE,
                    HttpServletResponse.SC_FORBIDDEN);
            request.getRequestDispatcher("/error").forward(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(false,
                new ApiError("AUTH_FORBIDDEN","Operation is not permitted",null), UUID.randomUUID().toString()));
    }
}
