package com.aloute.controller.api;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.dto.request.CreateReportRequest;
import com.aloute.dto.response.ReportResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;
    private final CurrentUserProvider currentUserProvider;
    @PostMapping public ResponseEntity<ApiResponse<ReportResponse>> create(@Valid @RequestBody CreateReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(reportService.create(currentUserProvider.requireUserId(),request)));
    }
}
