package com.aloute.service.impl;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.response.TopicResponse;
import com.aloute.entity.Topic;
import com.aloute.entity.User;
import com.aloute.entity.UserTopic;
import com.aloute.repository.TopicRepository;
import com.aloute.repository.UserRepository;
import com.aloute.repository.UserTopicRepository;
import com.aloute.service.TopicService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class TopicServiceImpl implements TopicService {
    private final TopicRepository topicRepository;
    private final UserTopicRepository userTopicRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<TopicResponse>> list(String q, int page, int size) {
        Page<Topic> topicPage = topicRepository.searchTopics(q, PageRequest.of(Math.max(0, page), Math.max(1, size)));
        List<TopicResponse> list = topicPage.getContent().stream()
                .map(topic -> new TopicResponse(topic.getId(), topic.getName(), topic.getSlug()))
                .toList();
        return ApiResponse.page(list, PageMeta.from(topicPage));
    }

    @Override
    @Transactional(readOnly = true)
    public TopicResponse get(Long topicId) {
        return topicRepository.findById(topicId)
                .map(topic -> new TopicResponse(topic.getId(), topic.getName(), topic.getSlug()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "Topic not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public TopicResponse getByName(String name) {
        return topicRepository.findByName(name)
                .or(() -> topicRepository.findBySlug(name))
                .map(topic -> new TopicResponse(topic.getId(), topic.getName(), topic.getSlug()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "Topic not found"));
    }

    @Override
    @Transactional
    public TopicResponse follow(Long userId, Long topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "Topic not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        if (!userTopicRepository.existsByUserIdAndTopicId(userId, topicId)) {
            userTopicRepository.save(new UserTopic(user, topic));
        }
        return new TopicResponse(topic.getId(), topic.getName(), topic.getSlug());
    }

    @Override
    @Transactional
    public void unfollow(Long userId, Long topicId) {
        userTopicRepository.deleteByUserIdAndTopicId(userId, topicId);
    }
}
