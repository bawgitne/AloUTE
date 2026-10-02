package com.aloute.common.exception;

import java.util.List;
import java.util.Map;

public class RequestValidationException extends RuntimeException {
    private final Map<String, List<String>> fieldErrors;

    public RequestValidationException(Map<String, List<String>> fieldErrors) {
        super("Request validation failed");
        this.fieldErrors = fieldErrors;
    }

    public Map<String, List<String>> getFieldErrors() {
        return fieldErrors;
    }
}
