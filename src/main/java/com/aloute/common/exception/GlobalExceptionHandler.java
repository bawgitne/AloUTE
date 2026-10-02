package com.aloute.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(error(ex.getCode(), ex.getMessage(), null, request));
    }

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ErrorResponse> handleRequestValidation(
            RequestValidationException ex, HttpServletRequest request) {
        return ResponseEntity.unprocessableEntity()
                .body(error("VALIDATION_FAILED", "Request validation failed", ex.getFieldErrors(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.computeIfAbsent(fieldError.getField(), key -> new ArrayList<>())
                    .add(fieldError.getDefaultMessage());
        }
        return ResponseEntity.unprocessableEntity()
                .body(error("VALIDATION_FAILED", "Request validation failed", fieldErrors, request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String field = violation.getPropertyPath().toString();
            fieldErrors.computeIfAbsent(field, key -> new ArrayList<>()).add(violation.getMessage());
        });
        return ResponseEntity.badRequest()
                .body(error("VALIDATION_FAILED", "Request validation failed", fieldErrors, request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("INTERNAL_ERROR", "Unexpected server failure", null, request));
    }

    private ErrorResponse error(String code, String message, Map<String, List<String>> fieldErrors,
                                HttpServletRequest request) {
        Object requestId = request.getAttribute("traceId");
        String traceId = requestId != null ? requestId.toString() : UUID.randomUUID().toString();
        return new ErrorResponse(false, new ApiError(code, message, fieldErrors), traceId);
    }
}
