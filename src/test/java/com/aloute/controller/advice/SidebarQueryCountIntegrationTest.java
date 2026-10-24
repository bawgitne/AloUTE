package com.aloute.controller.advice;

import com.aloute.dto.response.TopicResponse;
import com.aloute.entity.Topic;
import com.aloute.repository.TopicRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Query-count regression tests for the global sidebar rendering
 * (SidebarTopicsAdvisor).
 *
 * The advisor runs as a @ModelAttribute on every controller, including REST
 * handlers whose JSON responses discard the model, so the sidebar previously
 * paid its two topic statements on every request. These tests pin the
 * statement counts and the TTL-cache behavior (cache hit is free, cache
 * respects expiry so newly created topics appear). Actual statement counts
 * are printed for the evidence log.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class SidebarQueryCountIntegrationTest {

    private static final int TOPIC_COUNT = 10;

    @Autowired private SidebarTopicsAdvisor advisor;
    @Autowired private TopicRepository topicRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mockMvc;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void seedTopicsAndResetCache() {
        for (int i = 0; i < TOPIC_COUNT; i++) {
            String slug = "sidebar-count-topic-" + i;
            if (topicRepository.findBySlug(slug).isEmpty()) {
                Topic t = new Topic();
                t.setName("Sidebar Count Topic " + i);
                t.setSlug(slug);
                topicRepository.save(t);
            }
        }
        entityManager.flush();
        entityManager.clear();
        // start every test with caching disabled so calls are order-independent
        advisor.sidebarCacheTtlSeconds = 0;
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private long countStatements(Runnable call) {
        Statistics stats = statistics();
        stats.clear();
        call.run();
        return stats.getPrepareStatementCount();
    }

    @Test
    @DisplayName("Sidebar assembly stays bounded: two statements per uncached render")
    void advisorQueryCountBounded() {
        var topics = new java.util.concurrent.atomic.AtomicReference<List<TopicResponse>>(null);
        long queries = countStatements(() -> topics.set(advisor.sidebarTopics(new ExtendedModelMap())));
        System.out.printf("[sidebar-query-count] uncached advisor render: %d statements%n", queries);
        // one count + one content query for the bounded (6 rows) topic page
        assertThat(queries).as("JDBC statements for one uncached sidebar render").isLessThanOrEqualTo(3);
        assertThat(topics.get()).isNotNull();
        assertThat(topics.get().size()).isLessThanOrEqualTo(6);
        assertThat(topics.get()).allSatisfy(t -> {
            assertThat(t.id()).isNotNull();
            assertThat(t.name()).isNotBlank();
            assertThat(t.slug()).isNotBlank();
        });

        // repeated uncached renders cost the same (growth bounded by MAX_SIDEBAR_TOPICS)
        long queriesAgain = countStatements(() -> advisor.sidebarTopics(new ExtendedModelMap()));
        System.out.printf("[sidebar-query-count] second uncached render: %d statements%n", queriesAgain);
        assertThat(queriesAgain).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("TTL cache serves repeat renders for free and expires so new topics appear")
    void ttlCacheSkipsQueriesUntilExpiry() throws Exception {
        advisor.sidebarCacheTtlSeconds = 60;
        long warmup = countStatements(() -> advisor.sidebarTopics(new ExtendedModelMap()));
        assertThat(warmup).isLessThanOrEqualTo(3);

        long cached = countStatements(() -> advisor.sidebarTopics(new ExtendedModelMap()));
        System.out.printf("[sidebar-query-count] cached advisor render: %d statements%n", cached);
        assertThat(cached).as("JDBC statements for a cache-hit sidebar render").isEqualTo(0);

        // after expiry the advisor queries again (newly created topics become visible)
        advisor.sidebarCacheTtlSeconds = 1;
        Thread.sleep(1200);
        long expired = countStatements(() -> advisor.sidebarTopics(new ExtendedModelMap()));
        System.out.printf("[sidebar-query-count] expired-cache render: %d statements%n", expired);
        assertThat(expired).as("JDBC statements after TTL expiry").isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("REST endpoints no longer pay sidebar queries on every request")
    void restEndpointsDoNotPaySidebarQueries() throws Exception {
        advisor.sidebarCacheTtlSeconds = 60;
        long warmup = countStatements(() -> advisor.sidebarTopics(new ExtendedModelMap()));
        assertThat(warmup).isLessThanOrEqualTo(3);

        Statistics stats = statistics();
        stats.clear();
        mockMvc.perform(get("/api/topics").param("page", "0").param("size", "1"))
                .andExpect(status().isOk());
        long queries = stats.getPrepareStatementCount();
        System.out.printf("[sidebar-query-count] GET /api/topics with warm sidebar cache: %d statements%n", queries);
        // only the topic list count + content; the sidebar cache hit is free.
        // Pre-optimization this endpoint paid the two sidebar statements too (~4).
        assertThat(queries).as("JDBC statements for GET /api/topics").isLessThanOrEqualTo(2);
    }
}
