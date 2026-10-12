package com.aloute.common.exception;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class RequestValidationException extends RuntimeException {
    private final Map<String, List<String>> fieldErrors;

    public RequestValidationException(Map<String, List<String>> fieldErrors) {
        super("Request validation failed");
        this.fieldErrors = fieldErrors;
    }
}
