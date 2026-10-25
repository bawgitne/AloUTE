package com.aloute.dto.response;

public record PostCapabilitiesResponse(boolean canEdit, boolean canDelete,
                                       boolean canReply, boolean canQuote,
                                       boolean canApprove, boolean canRemove) {
    /** Compatibility constructor for pre-moderation call sites and tests. */
    public PostCapabilitiesResponse(boolean canEdit, boolean canDelete, boolean canReply, boolean canQuote) {
        this(canEdit, canDelete, canReply, canQuote, false, false);
    }
}
