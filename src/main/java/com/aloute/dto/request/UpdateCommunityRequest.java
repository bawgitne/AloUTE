package com.aloute.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateCommunityRequest(
        @Size(max = 100) String name,
        @Size(max = 2000) String description,
        Boolean isPrivate,
        Long avatarMediaId,
        Long coverMediaId,
        /** Owner-only setting: submissions of ordinary members require approval. */
        Boolean requirePostApproval
) {
    /** Compatibility constructor for pre-approval call sites and tests. */
    public UpdateCommunityRequest(String name, String description, Boolean isPrivate,
                                  Long avatarMediaId, Long coverMediaId) {
        this(name, description, isPrivate, avatarMediaId, coverMediaId, null);
    }
}
