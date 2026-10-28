package com.aloute.security.handler;

import com.aloute.common.exception.ApiError;
import com.aloute.common.exception.ErrorResponse;
import com.aloute.common.web.RequestTypeSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuthenticationFailureHandler implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;
    @Override public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        // BL-22: a browser navigating to a protected page is sent to the login
        // page; API/JSON callers keep the canonical JSON envelope.
        if (RequestTypeSupport.isHtmlPageRequest(request)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(false,
                new ApiError("AUTH_UNAUTHENTICATED","Authentication required or credential invalid",null), UUID.randomUUID().toString()));
    }
}
