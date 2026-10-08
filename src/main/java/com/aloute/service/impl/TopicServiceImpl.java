package com.aloute.service.impl;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.response.TopicResponse;
import com.aloute.repository.TopicRepository;
import com.aloute.service.TopicService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TopicServiceImpl implements TopicService {
    private final TopicRepository topicRepository;

    public TopicServiceImpl(TopicRepository topicRepository) {
        this.topicRepository = topicRepository;
    }

    @Override @Transactional(readOnly = true)
    public ApiResponse<List<TopicResponse>> list(String q, int page, int size) {
        long total = topicRepository.countTopicsFromSp(q);
        return ApiResponse.page(topicRepository.listTopicsFromSp(q, page * size, size).stream()
                .map(topic -> new TopicResponse(topic.getId(), topic.getName(), topic.getSlug())).toList(),
                meta(page, size, total));
    }

    @Override @Transactional(readOnly = true)
    public TopicResponse get(Long topicId) {
        return topicRepository.getTopicFromSp(topicId)
                .map(topic -> new TopicResponse(topic.getId(), topic.getName(), topic.getSlug()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "Topic not found"));
    }

    @Override @Transactional
    public TopicResponse follow(Long userId, Long topicId) {
        topicRepository.followTopicFromSp(userId, topicId);
        return get(topicId);
    }

    @Override @Transactional
    public void unfollow(Long userId, Long topicId) {
        topicRepository.unfollowTopicFromSp(userId, topicId);
    }

    private PageMeta meta(int page, int size, long total) {
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageMeta(page, size, total, totalPages, page + 1 < totalPages, page > 0);
    }
}
