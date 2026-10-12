package com.aloute.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setAttribute("traceId", "test-trace-id-123");
    }

    @Test
    @DisplayName("handleAppException with ErrorCode returns mapped status, code, and traceId")
    void handleAppExceptionWithErrorCode() {
        AppException ex = new AppException(ErrorCode.POST_NOT_FOUND);
        ResponseEntity<ErrorResponse> response = handler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("POST_NOT_FOUND");
        assertThat(response.getBody().error().message()).isEqualTo("Post not found");
        assertThat(response.getBody().traceId()).isEqualTo("test-trace-id-123");
    }

    @Test
    @DisplayName("handleAppException with ResourceNotFoundException returns 404")
    void handleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Post", 999L);
        ResponseEntity<ErrorResponse> response = handler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().error().message()).contains("Post not found with id: 999");
    }

    @Test
    @DisplayName("handleAccessDenied returns 403 AUTH_FORBIDDEN")
    void handleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("AUTH_FORBIDDEN");
    }

    @Test
    @DisplayName("handleAuthentication returns 401 AUTH_UNAUTHENTICATED")
    void handleAuthentication() {
        BadCredentialsException ex = new BadCredentialsException("Bad creds");
        ResponseEntity<ErrorResponse> response = handler.handleAuthentication(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("AUTH_UNAUTHENTICATED");
    }

    @Test
    @DisplayName("handleDataIntegrity returns 409 CONFLICT")
    void handleDataIntegrity() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Unique constraint");
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("CONFLICT");
    }

    @Test
    @DisplayName("handleRequestValidation returns 422 VALIDATION_FAILED with fieldErrors")
    void handleRequestValidation() {
        RequestValidationException ex = new RequestValidationException(Map.of("content", List.of("Must not be blank")));
        ResponseEntity<ErrorResponse> response = handler.handleRequestValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().error().fieldErrors()).containsKey("content");
    }

    @Test
    @DisplayName("handleUnexpected returns 500 INTERNAL_ERROR")
    void handleUnexpected() {
        RuntimeException ex = new RuntimeException("Unexpected db crash");
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().error().message()).isEqualTo("Unexpected server failure");
    }
}
