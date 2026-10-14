package com.aloute.service.admin;

import com.aloute.dto.response.admin.AdminPostDetailResponse;
import com.aloute.dto.response.admin.AdminPostSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminPostService {

    Page<AdminPostSummaryResponse> getPosts(String query, Long authorId, Long communityId, String contentState, Boolean reportedOnly, Pageable pageable);

    AdminPostDetailResponse getPostDetail(Long postId);
}
