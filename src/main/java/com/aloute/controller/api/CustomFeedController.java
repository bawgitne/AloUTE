package com.aloute.controller.api;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * Custom feeds are contract-only (OpenAPI x-implementation-status): there is no
 * feed creation path and no feed entity, so the list is the documented empty
 * stub and any concrete feedId must 404 instead of serving wrong-semantics data.
 */
@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class CustomFeedController {

    @GetMapping
    public ApiResponse<List<Object>> listCustomFeeds(@PageableDefault(size = 20) Pageable pageable) {
        PageImpl<Object> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        return ApiResponse.page(emptyPage.getContent(), PageMeta.from(emptyPage));
    }

    @GetMapping("/{feedId}/posts")
    public ApiResponse<Void> getFeedPosts(
            @PathVariable Long feedId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        throw new ApiException(HttpStatus.NOT_FOUND, "FEED_NOT_FOUND",
                "Custom feed not found. Custom feed creation is not available yet.");
    }
}
