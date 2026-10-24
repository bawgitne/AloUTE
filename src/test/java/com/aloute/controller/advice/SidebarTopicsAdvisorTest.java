package com.aloute.controller.advice;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.response.TopicResponse;
import com.aloute.service.TopicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SidebarTopicsAdvisorTest {

    @Mock private TopicService topicService;

    private SidebarTopicsAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = new SidebarTopicsAdvisor(topicService);
        advisor.sidebarCacheTtlSeconds = 0;
    }

    private TopicResponse topic(long id, String name) {
        return new TopicResponse(id, name, "slug-" + id);
    }

    private void stubList(List<TopicResponse> data) {
        when(topicService.list(null, 0, 6)).thenReturn(ApiResponse.success(data));
    }

    @Test
    @DisplayName("Sidebar keeps at most MAX_SIDEBAR_TOPICS entries")
    void limitsTopicCount() {
        stubList(java.util.stream.LongStream.rangeClosed(1, 9)
                .mapToObj(i -> topic(i, "T" + i)).toList());

        List<TopicResponse> topics = advisor.sidebarTopics(new ExtendedModelMap());

        assertThat(topics).hasSize(6);
    }

    @Test
    @DisplayName("Sidebar falls back to an empty list when the lookup fails")
    void fallsBackOnFailure() {
        when(topicService.list(null, 0, 6)).thenThrow(new RuntimeException("db down"));

        List<TopicResponse> topics = advisor.sidebarTopics(new ExtendedModelMap());

        assertThat(topics).isEmpty();
    }

    @Test
    @DisplayName("Sidebar falls back to an empty list when the topic table is empty")
    void fallsBackWhenTableEmpty() {
        stubList(List.of());

        List<TopicResponse> topics = advisor.sidebarTopics(new ExtendedModelMap());

        assertThat(topics).isEmpty();
    }

    @Test
    @DisplayName("TTL cache serves repeat renders without touching the service; TTL 0 always queries")
    void ttlCacheBehavior() {
        stubList(List.of(topic(1, "Only topic")));

        advisor.sidebarCacheTtlSeconds = 60;
        advisor.sidebarTopics(new ExtendedModelMap());
        advisor.sidebarTopics(new ExtendedModelMap());
        verify(topicService, times(1)).list(null, 0, 6);

        advisor.sidebarCacheTtlSeconds = 0;
        advisor.sidebarTopics(new ExtendedModelMap());
        verify(topicService, times(2)).list(null, 0, 6);
    }

    @Test
    @DisplayName("Failed lookups are never cached")
    void failuresAreNotCached() {
        when(topicService.list(null, 0, 6)).thenThrow(new RuntimeException("db down"));
        advisor.sidebarCacheTtlSeconds = 60;
        advisor.sidebarTopics(new ExtendedModelMap());

        // re-stub with doReturn: when() would re-invoke the throwing stubbing
        org.mockito.Mockito.doReturn(ApiResponse.success(List.of(topic(1, "Recovered"))))
                .when(topicService).list(null, 0, 6);
        List<TopicResponse> topics = advisor.sidebarTopics(new ExtendedModelMap());

        assertThat(topics).hasSize(1);
        // two service calls total: the failed lookup was not served from cache
        verify(topicService, times(2)).list(null, 0, 6);
    }
}
