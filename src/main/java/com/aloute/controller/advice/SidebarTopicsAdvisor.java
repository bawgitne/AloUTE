package com.aloute.controller.advice;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.TopicResponse;
import com.aloute.service.TopicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * Supplies the global sidebar "trending topics" data for every page render.
 * Real topics come from the topics table; the fragment falls back to a static
 * list when the table is empty or the lookup fails.
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class SidebarTopicsAdvisor {

    private static final int MAX_SIDEBAR_TOPICS = 6;

    private final TopicService topicService;

    @ModelAttribute("sidebarTopics")
    public List<TopicResponse> sidebarTopics(Model model) {
        try {
            ApiResponse<List<TopicResponse>> response = topicService.list(null, 0, MAX_SIDEBAR_TOPICS);
            if (response != null && response.getData() != null) {
                return response.getData().stream().limit(MAX_SIDEBAR_TOPICS).toList();
            }
        } catch (Exception ex) {
            log.warn("Could not load sidebar topics: {}", ex.getMessage());
        }
        return List.of();
    }
}
