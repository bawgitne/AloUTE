package com.aloute.service.admin;

import com.aloute.dto.response.admin.AdminPostDetailResponse;
import com.aloute.dto.response.admin.AdminPostSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminPostService {

    Page<AdminPostSummaryResponse> getPosts(Long viewerAdminId, String query, Long authorId, Long communityId, String contentState, Boolean reportedOnly, Pageable pageable);

    AdminPostDetailResponse getPostDetail(Long viewerAdminId, Long postId);

    default Page<AdminPostSummaryResponse> getPosts(String query, Long authorId, Long communityId, String contentState, Boolean reportedOnly, Pageable pageable) {
        return getPosts(null, query, authorId, communityId, contentState, reportedOnly, pageable);
    }

    default AdminPostDetailResponse getPostDetail(Long postId) {
        return getPostDetail(null, postId);
    }

    Page<AdminPostSummaryResponse> getUserPosts(Long userId, String contentState, Pageable pageable, Long viewerId);

    default Page<AdminPostSummaryResponse> getUserPosts(Long userId, String contentState, Pageable pageable) {
        return getUserPosts(userId, contentState, pageable, null);
    }
}
