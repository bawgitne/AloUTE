package com.aloute.common.exception;

public record ErrorResponse(boolean success, ApiError error, String traceId) {}
