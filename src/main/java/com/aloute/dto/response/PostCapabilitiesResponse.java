package com.aloute.dto.response;

public record PostCapabilitiesResponse(boolean canEdit, boolean canDelete,
                                       boolean canReply, boolean canQuote) {}
