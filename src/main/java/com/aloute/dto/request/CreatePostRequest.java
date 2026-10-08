package com.aloute.dto.request;

import com.aloute.entity.PostVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreatePostRequest(
        @NotBlank @Size(max = 4000) String content,
        @NotNull PostVisibility visibility,
        Boolean allowReply,
        Boolean allowQuote,
        List<Long> mediaIds,
        List<Long> mentionedUserIds,
        List<Long> topicIds,
        Object poll
) {}
