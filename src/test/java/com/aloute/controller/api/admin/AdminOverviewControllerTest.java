package com.aloute.controller.api.admin;

import com.aloute.dto.response.admin.AdminOverviewResponse;
import com.aloute.service.admin.AdminOverviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOverviewControllerTest {

    private AdminOverviewService adminOverviewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminOverviewService = mock(AdminOverviewService.class);
        AdminOverviewController controller = new AdminOverviewController(adminOverviewService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/admin/overview returns 200 with dashboard metrics")
    void getOverviewSuccess() throws Exception {
        AdminOverviewResponse.OverviewMetrics metrics = new AdminOverviewResponse.OverviewMetrics(
                100L, 80L, 500L, 25L, 5L, 2L, 10L
        );
        AdminOverviewResponse response = new AdminOverviewResponse(
                Instant.now(),
                "7d",
                metrics,
                List.of(new AdminOverviewResponse.DailyActivity("2026-10-14", 5L, 20L)),
                List.of(1L, 2L),
                List.of(10L)
        );

        when(adminOverviewService.getOverview()).thenReturn(response);

        mockMvc.perform(get("/api/admin/overview")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.period").value("7d"))
                .andExpect(jsonPath("$.data.metrics.totalUsers").value(100))
                .andExpect(jsonPath("$.data.metrics.activeUsers").value(80))
                .andExpect(jsonPath("$.data.metrics.totalPosts").value(500))
                .andExpect(jsonPath("$.data.metrics.pendingReports").value(5));
    }
}
