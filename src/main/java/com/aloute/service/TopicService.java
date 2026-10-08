package com.aloute.service;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.TopicResponse;
import java.util.List;

public interface TopicService {
    ApiResponse<List<TopicResponse>> list(String q, int page, int size);
    TopicResponse get(Long topicId);
    TopicResponse follow(Long userId, Long topicId);
    void unfollow(Long userId, Long topicId);
}
