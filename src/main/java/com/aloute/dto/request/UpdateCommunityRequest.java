package com.aloute.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateCommunityRequest(
        @Size(max = 100) String name,
        @Size(max = 2000) String description,
        Boolean isPrivate,
        Long avatarMediaId,
        Long coverMediaId
) {}
