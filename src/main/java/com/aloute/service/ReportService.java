package com.aloute.service;

import com.aloute.dto.request.CreateReportRequest;
import com.aloute.dto.response.ReportResponse;

public interface ReportService {
    ReportResponse create(Long reporterId, CreateReportRequest request);
}
