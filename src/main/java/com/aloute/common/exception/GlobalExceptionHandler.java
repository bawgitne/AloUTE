package com.aloute.common.exception;

import com.aloute.common.web.RequestTypeSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Global error mapping.
 *
 * <p>BL-22: the representation is content-negotiated per request. API/JSON
 * callers (everything under {@code /api/**} and {@code /ws/**}, or callers that
 * declare {@code Accept: application/json}) always receive the canonical
 * {@link ErrorResponse} envelope. Browser page navigation receives branded HTML
 * error pages ({@code error/403}, {@code error/404}, {@code error/500}) and a
 * login redirect for 401 — never a raw JSON body on a page.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public Object handleAppException(AppException ex, HttpServletRequest request) {
        log.warn("Application exception [{}]: {}", ex.getCode(), ex.getMessage());
        return reply(ex.getStatus(), ex.getCode(), ex.getMessage(), null, request);
    }

    @ExceptionHandler(RequestValidationException.class)
    public Object handleRequestValidation(
            RequestValidationException ex, HttpServletRequest request) {
        log.warn("Request validation failed: {}", ex.getFieldErrors());
        return reply(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.VALIDATION_FAILED.getCode(),
                "Request validation failed", ex.getFieldErrors(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        for (org.springframework.validation.FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.computeIfAbsent(fieldError.getField(), key -> new ArrayList<>())
                    .add(fieldError.getDefaultMessage());
        }
        log.warn("Method argument validation failed: {}", fieldErrors);
        return reply(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.VALIDATION_FAILED.getCode(),
                "Request validation failed", fieldErrors, request);
    }

    /**
     * Spring 6.1 raises HandlerMethodValidationException when built-in method validation
     * (e.g. @Positive on a @PathVariable) or a request-body constraint on a controller
     * method that also carries parameter constraints fails. Without this handler those
     * requests fall through to the generic 500; they must be 422 like every other
     * validation failure.
     */
    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    public Object handleHandlerMethodValidation(
            org.springframework.web.method.annotation.HandlerMethodValidationException ex, HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        for (org.springframework.validation.method.ParameterValidationResult result : ex.getAllValidationResults()) {
            for (var error : result.getResolvableErrors()) {
                fieldErrors.computeIfAbsent(result.getMethodParameter().getParameterName(), key -> new ArrayList<>())
                        .add(error.getDefaultMessage());
            }
        }
        log.warn("Handler method validation failed: {}", fieldErrors);
        return reply(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.VALIDATION_FAILED.getCode(),
                "Request validation failed", fieldErrors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String field = violation.getPropertyPath().toString();
            fieldErrors.computeIfAbsent(field, key -> new ArrayList<>()).add(violation.getMessage());
        });
        log.warn("Constraint violation: {}", fieldErrors);
        return reply(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.VALIDATION_FAILED.getCode(),
                "Request validation failed", fieldErrors, request);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public Object handleAccessDenied(org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return reply(HttpStatus.FORBIDDEN, ErrorCode.AUTH_FORBIDDEN.getCode(),
                "Operation is not permitted", null, request);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public Object handleAuthentication(org.springframework.security.core.AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return reply(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_UNAUTHENTICATED.getCode(),
                "Authentication required or credential invalid", null, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return reply(HttpStatus.CONFLICT, ErrorCode.CONFLICT.getCode(),
                "Data integrity constraint violated", null, request);
    }

    @ExceptionHandler({
            org.springframework.orm.ObjectOptimisticLockingFailureException.class,
            jakarta.persistence.OptimisticLockException.class
    })
    public Object handleOptimisticLockConflict(Exception ex, HttpServletRequest request) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        return reply(HttpStatus.CONFLICT, ErrorCode.REPORT_VERSION_CONFLICT.getCode(),
                "Report was modified by another transaction", null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("HTTP method not supported: {}", ex.getMessage());
        return reply(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getCode(),
                ex.getMessage(), null, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Object handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("HTTP media type not supported: {}", ex.getMessage());
        return reply(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode(),
                ex.getMessage(), null, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed HTTP message body: {}", ex.getMessage());
        return reply(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.getCode(),
                "Malformed request payload", null, request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public Object handleBadRequestParameters(Exception ex, HttpServletRequest request) {
        log.warn("Bad request parameter: {}", ex.getMessage());
        return reply(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.getCode(),
                ex.getMessage(), null, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getResourcePath());
        return reply(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                "Resource not found", null, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Upload size exceeded limit: {}", ex.getMessage());
        return reply(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.MEDIA_TOO_LARGE.getCode(),
                ErrorCode.MEDIA_TOO_LARGE.getDefaultMessage(), null, request);
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled unexpected exception: ", ex);
        return reply(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.getCode(),
                "Unexpected server failure", null, request);
    }

    /**
     * Picks the JSON envelope or a branded HTML error view based on the request.
     * A 401 on a browser navigation redirects to the login page.
     */
    private Object reply(HttpStatus status, String code, String message,
                         Map<String, List<String>> fieldErrors, HttpServletRequest request) {
        if (RequestTypeSupport.isHtmlPageRequest(request)) {
            return htmlView(status, request);
        }
        return ResponseEntity.status(status)
                .body(new ErrorResponse(false, new ApiError(code, message, fieldErrors), traceId(request)));
    }

    private ModelAndView htmlView(HttpStatus status, HttpServletRequest request) {
        if (status == HttpStatus.UNAUTHORIZED) {
            return new ModelAndView("redirect:/login");
        }
        String view;
        if (status == HttpStatus.NOT_FOUND) {
            view = "error/404";
        } else if (status == HttpStatus.FORBIDDEN) {
            view = "error/403";
        } else {
            // Keep the true status so a genuine server fault is never shown as a 404.
            view = "error/500";
        }
        ModelAndView mv = new ModelAndView(view);
        mv.setStatus(status);
        mv.addObject("path", request.getRequestURI());
        return mv;
    }

    private String traceId(HttpServletRequest request) {
        Object requestId = request != null ? request.getAttribute("traceId") : null;
        return requestId != null ? requestId.toString() : UUID.randomUUID().toString();
    }
}