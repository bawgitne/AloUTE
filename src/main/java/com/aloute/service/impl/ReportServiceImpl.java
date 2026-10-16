package com.aloute.service.impl;

import lombok.RequiredArgsConstructor;
import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.common.exception.RequestValidationException;
import com.aloute.dto.request.CreateReportRequest;
import com.aloute.dto.response.ReportResponse;
import com.aloute.entity.Post;
import com.aloute.entity.Report;
import com.aloute.entity.User;
import com.aloute.repository.PostRepository;
import com.aloute.repository.ReportRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.PostService;
import com.aloute.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class ReportServiceImpl implements ReportService {
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostService postService;

    @Override @Transactional
    public ReportResponse create(Long reporterId, CreateReportRequest request) {
        boolean userTarget = request.reportedUserId() != null;
        boolean postTarget = request.reportedPostId() != null;
        if (userTarget == postTarget) {
            throw new RequestValidationException(Map.of("target", List.of("Exactly one of reportedUserId or reportedPostId is required")));
        }
        User reporter = requireUser(reporterId);
        Report report = new Report();
        report.setReporter(reporter);
        if (userTarget) {
            if (reporterId.equals(request.reportedUserId())) {
                throw new ApiException(ErrorCode.REPORT_SELF_NOT_ALLOWED, "Cannot report yourself");
            }
            report.setReportedUser(requireUser(request.reportedUserId()));
        }
        if (postTarget) {
            Post post = postRepository.findById(request.reportedPostId())
                    .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND, "Post not found"));
            if (post.getDeletedAt() != null) {
                throw new ApiException(ErrorCode.POST_NOT_FOUND, "Post not found");
            }
            if (!postService.isPostVisibleTo(post, reporterId)) {
                throw new ApiException(ErrorCode.POST_ACCESS_DENIED, "You do not have permission to access this post");
            }
            report.setReportedPostId(post.getId());
        }
        report.setReason(request.reason());
        report.setDescription(trimToNull(request.description()));
        Report saved = reportRepository.save(report);
        return new ReportResponse(saved.getId(), saved.getReportedUser()==null?null:saved.getReportedUser().getId(),
                saved.getReportedPostId(), saved.getReason(), saved.getDescription(), saved.getStatus(), saved.getCreatedAt());
    }
    private User requireUser(Long id) { return userRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","User not found")); }
    private String trimToNull(String s) { if (s==null) return null; String v=s.trim(); return v.isEmpty()?null:v; }
}
