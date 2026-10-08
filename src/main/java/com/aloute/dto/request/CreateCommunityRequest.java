package com.aloute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCommunityRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String slug,
        @Size(max = 2000) String description,
        @NotNull Boolean isPrivate,
        Long avatarMediaId,
        Long coverMediaId
) {}
