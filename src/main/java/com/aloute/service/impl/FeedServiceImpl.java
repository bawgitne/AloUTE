package com.aloute.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aloute.dto.response.PostResponse;
import com.aloute.entity.Follow;
import com.aloute.entity.Post;
import com.aloute.entity.User;
import com.aloute.repository.FollowRepository;
import com.aloute.repository.PostSpecifications;
import com.aloute.repository.PostRepository;
import com.aloute.service.FeedService;
import com.aloute.service.PostService;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
        Specification<Post> spec = (root, query, cb) -> cb.isNull(root.get("parentPost"));
        spec = spec.and(postService.buildVisibilitySpec(currentUserId))
                .and(PostSpecifications.excludeMutedAuthors(currentUserId));
        return postRepository.findAll(spec, pageable)
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
                .map(User::getId)
                .getContent();

        if (followingIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        Specification<Post> spec = (root, query, cb) -> cb.and(
                cb.isNull(root.get("parentPost")),
                root.get("user").get("id").in(followingIds)
        );
        spec = spec.and(postService.buildVisibilitySpec(currentUserId))
                .and(PostSpecifications.excludeMutedAuthors(currentUserId));

        return postRepository.findAll(spec, pageable)
                .map(post -> postService.toResponse(post, currentUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getExploreFeed(Pageable pageable, Long currentUserId) {
        Specification<Post> spec = (root, query, cb) -> {
            Predicate p = cb.isNull(root.get("parentPost"));
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                Expression<Number> score = cb.sum(
                        cb.sum(cb.prod(root.get("likeCount"), 2), cb.prod(root.get("repostCount"), 3)),
                        root.get("replyCount")
                );
                query.orderBy(cb.desc(score), cb.desc(root.get("createdAt")));
            }
            return p;
        };
        spec = spec.and(postService.buildVisibilitySpec(currentUserId))
                .and(PostSpecifications.excludeMutedAuthors(currentUserId));

        return postRepository.findAll(spec, pageable)
                .map(post -> postService.toResponse(post, currentUserId));
    }
}
