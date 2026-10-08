package com.aloute.service;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.request.CreatePostRequest;
import com.aloute.dto.request.UpdatePostRequest;
import com.aloute.dto.response.PostResponse;
import java.util.List;

public interface PostService {
    ApiResponse<List<PostResponse>> listPublic(int page, int size, Long viewerId);
    PostResponse get(Long postId, Long viewerId);
    PostResponse create(Long userId, CreatePostRequest request, Long communityId, Long parentPostId, Long quotedPostId);
    PostResponse update(Long userId, Long postId, UpdatePostRequest request);
    void delete(Long userId, Long postId);
    ApiResponse<List<PostResponse>> replies(Long postId, int page, int size, Long viewerId);
    ApiResponse<List<PostResponse>> communityPosts(Long communityId, int page, int size, Long viewerId);
    ApiResponse<List<PostResponse>> topicPosts(Long topicId, int page, int size, Long viewerId);
    PostResponse like(Long userId, Long postId);
    void unlike(Long userId, Long postId);
    PostResponse bookmark(Long userId, Long postId);
    void removeBookmark(Long userId, Long postId);
    PostResponse repost(Long userId, Long postId);
    void removeRepost(Long userId, Long postId);
}
