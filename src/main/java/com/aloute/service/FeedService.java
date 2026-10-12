package com.aloute.service;

import com.aloute.dto.response.PostResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FeedService {
    Page<PostResponse> getHomeFeed(Pageable pageable, Long currentUserId);
    Page<PostResponse> getFollowingFeed(Pageable pageable, Long currentUserId);
    Page<PostResponse> getExploreFeed(Pageable pageable, Long currentUserId);
}
