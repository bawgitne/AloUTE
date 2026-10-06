package com.aloute.common.exception;

import java.util.List;
import java.util.Map;

public record ApiError(String code, String message, Map<String, List<String>> fieldErrors) {}
