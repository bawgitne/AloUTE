package com.aloute.service.admin.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.response.admin.AdminPostDetailResponse;
import com.aloute.dto.response.admin.AdminPostSummaryResponse;
import com.aloute.entity.Post;
import com.aloute.entity.PostModerationRemoval;
import com.aloute.entity.Report;
import com.aloute.repository.PostModerationRemovalRepository;
import com.aloute.repository.PostRepository;
import com.aloute.repository.ReportRepository;
import com.aloute.service.admin.AdminMappingHelper;
import com.aloute.service.admin.AdminPostService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostServiceImpl implements AdminPostService {

    private final PostRepository postRepository;
    private final PostModerationRemovalRepository postModerationRemovalRepository;
    private final ReportRepository reportRepository;
    private final AdminMappingHelper adminMappingHelper;

    @Override
    public Page<AdminPostSummaryResponse> getPosts(String query, Long authorId, Long communityId, String contentState, Boolean reportedOnly, Pageable pageable) {
        Specification<Post> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (authorId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), authorId));
            }
            if (communityId != null) {
                predicates.add(cb.equal(root.get("community").get("id"), communityId));
            }
            if (contentState != null && !contentState.isBlank()) {
                if ("ACTIVE".equalsIgnoreCase(contentState)) {
                    predicates.add(cb.isNull(root.get("deletedAt")));
                } else if ("AUTHOR_DELETED".equalsIgnoreCase(contentState) || "ADMIN_REMOVED".equalsIgnoreCase(contentState)) {
                    predicates.add(cb.isNotNull(root.get("deletedAt")));
                }
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("content")), pattern));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return postRepository.findAll(spec, pageable)
                .map(this::toSummaryResponse);
    }

    @Override
    public AdminPostDetailResponse getPostDetail(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND, "Post not found"));

        PostModerationRemoval activeRemoval = postModerationRemovalRepository
                .findFirstByPostIdAndRestoredAtIsNullOrderByIdDesc(postId)
                .orElse(null);

        String contentState;
        if (post.getDeletedAt() == null) {
            contentState = "ACTIVE";
        } else if (activeRemoval != null) {
            contentState = "ADMIN_REMOVED";
        } else {
            contentState = "AUTHOR_DELETED";
        }

        boolean canRemove = "ACTIVE".equals(contentState);
        boolean canRestore = "ADMIN_REMOVED".equals(contentState);

        List<Long> relatedReportIds = reportRepository.findByReportedPostId(postId)
                .stream()
                .map(Report::getId)
                .toList();

        Long parentPostId = post.getParentPost() != null ? post.getParentPost().getId() : null;
        Long quotedPostId = post.getQuotedPost() != null ? post.getQuotedPost().getId() : null;
        Long activeRemovalId = activeRemoval != null ? activeRemoval.getId() : null;

        return new AdminPostDetailResponse(
                post.getId(),
                adminMappingHelper.toUserSummary(post.getUser()),
                post.getContent(),
                post.getVisibility(),
                contentState,
                parentPostId,
                quotedPostId,
                post.getCreatedAt(),
                post.getDeletedAt(),
                relatedReportIds,
                activeRemovalId,
                new AdminPostDetailResponse.PostCapabilities(canRemove, canRestore)
        );
    }

    private AdminPostSummaryResponse toSummaryResponse(Post post) {
        boolean activeRemovalExists = postModerationRemovalRepository.existsByPostIdAndRestoredAtIsNull(post.getId());
        String contentState;
        if (post.getDeletedAt() == null) {
            contentState = "ACTIVE";
        } else if (activeRemovalExists) {
            contentState = "ADMIN_REMOVED";
        } else {
            contentState = "AUTHOR_DELETED";
        }

        boolean canRemove = "ACTIVE".equals(contentState);
        boolean canRestore = "ADMIN_REMOVED".equals(contentState);

        long reportedCount = reportRepository.countByReportedPostId(post.getId());

        String excerpt = post.getContent() != null
                ? (post.getContent().length() > 100 ? post.getContent().substring(0, 100) + "..." : post.getContent())
                : "";

        Long parentPostId = post.getParentPost() != null ? post.getParentPost().getId() : null;
        Long quotedPostId = post.getQuotedPost() != null ? post.getQuotedPost().getId() : null;
        Long communityId = post.getCommunity() != null ? post.getCommunity().getId() : null;

        return new AdminPostSummaryResponse(
                post.getId(),
                adminMappingHelper.toUserSummary(post.getUser()),
                excerpt,
                parentPostId,
                quotedPostId,
                communityId,
                post.getVisibility(),
                contentState,
                reportedCount,
                post.getCreatedAt(),
                new AdminPostSummaryResponse.PostCapabilities(canRemove, canRestore)
        );
    }
}
