package com.aloute.dto.request;

import com.aloute.entity.PostVisibility;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(
        @Size(max = 4000) String content,
        PostVisibility visibility,
        Boolean allowReply,
        Boolean allowQuote
) {}
