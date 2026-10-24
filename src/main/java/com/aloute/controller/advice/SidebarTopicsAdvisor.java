package com.aloute.controller.advice;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.TopicResponse;
import com.aloute.service.TopicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * Supplies the global sidebar "trending topics" data for every page render.
 * Real topics come from the topics table; the fragment falls back to a static
 * list when the table is empty or the lookup fails.
 *
 * The advisor also runs for REST handlers whose model is discarded, so the
 * lookup used to pay two topic statements on every request. The result is
 * cached for a short TTL: it holds only public topic id/name/slug (never
 * user-specific data), and expiry bounds how long a newly created topic can
 * stay invisible in the sidebar. Set the TTL to 0 to disable caching.
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class SidebarTopicsAdvisor {

    private static final int MAX_SIDEBAR_TOPICS = 6;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final TopicService topicService;

    @Value("${aloute.sidebar.topics-cache-ttl-seconds:30}")
    long sidebarCacheTtlSeconds;

    private volatile List<TopicResponse> cachedTopics = List.of();
    private volatile long cachedAtNanos = Long.MIN_VALUE;

    @ModelAttribute("sidebarTopics")
    public List<TopicResponse> sidebarTopics(Model model) {
        long ttlNanos = sidebarCacheTtlSeconds * NANOS_PER_SECOND;
        if (ttlNanos > 0) {
            List<TopicResponse> snapshot = cachedTopics;
            if (!snapshot.isEmpty() && cachedAtNanos + ttlNanos > System.nanoTime()) {
                return snapshot;
            }
        }
        try {
            ApiResponse<List<TopicResponse>> response = topicService.list(null, 0, MAX_SIDEBAR_TOPICS);
            if (response != null && response.getData() != null) {
                List<TopicResponse> topics = response.getData().stream().limit(MAX_SIDEBAR_TOPICS).toList();
                if (ttlNanos > 0) {
                    cachedTopics = topics;
                    cachedAtNanos = System.nanoTime();
                }
                return topics;
            }
        } catch (Exception ex) {
            log.warn("Could not load sidebar topics: {}", ex.getMessage());
        }
        return List.of();
    }
}
