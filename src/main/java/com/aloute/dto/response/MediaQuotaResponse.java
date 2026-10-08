package com.aloute.dto.response;

public record MediaQuotaResponse(long usedBytes, long quotaBytes, long remainingBytes) {}
