package com.aloute.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aloute.dto.response.PostResponse;
import com.aloute.entity.Follow;
import com.aloute.repository.FollowRepository;
import com.aloute.repository.PostRepository;
import com.aloute.service.FeedService;
import com.aloute.service.PostService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Service
public class FeedServiceImpl implements FeedService {

    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final PostService postService;

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getHomeFeed(Pageable pageable, Long currentUserId) {
        return postRepository.findByParentPostIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(pageable)
                .map(post -> postService.toResponse(post, currentUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getFollowingFeed(Pageable pageable, Long currentUserId) {
        if (currentUserId == null) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        List<Long> followingIds = followRepository.findByFollower_Id(currentUserId, Pageable.unpaged())
                .map(Follow::getFollowing)
                .map(u -> u.getId())
                .getContent();

        if (followingIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        return postRepository.findByFollowingUsers(followingIds, pageable)
                .map(post -> postService.toResponse(post, currentUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getExploreFeed(Pageable pageable, Long currentUserId) {
        return postRepository.findExplorePosts(pageable)
                .map(post -> postService.toResponse(post, currentUserId));
    }
}
