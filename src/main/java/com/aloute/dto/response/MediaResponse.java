package com.aloute.dto.response;

import com.aloute.entity.MediaType;

public record MediaResponse(Long id, MediaType type, String url, Integer width,
                            Integer height, Double durationSeconds) {}
