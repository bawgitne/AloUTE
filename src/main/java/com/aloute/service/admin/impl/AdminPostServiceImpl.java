package com.aloute.service.admin.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.response.admin.AdminPostDetailResponse;
import com.aloute.dto.response.admin.AdminPostSummaryResponse;
import com.aloute.entity.Community;
import com.aloute.entity.Post;
import com.aloute.entity.PostModerationRemoval;
import com.aloute.entity.PostVisibility;
import com.aloute.entity.Report;
import com.aloute.repository.PostModerationRemovalRepository;
import com.aloute.repository.PostRepository;
import com.aloute.repository.ReportRepository;
import com.aloute.service.admin.AdminMappingHelper;
import com.aloute.service.admin.AdminPostService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostServiceImpl implements AdminPostService {

    private static final Set<String> VALID_CONTENT_STATES = Set.of("ACTIVE", "AUTHOR_DELETED", "ADMIN_REMOVED");

    private final PostRepository postRepository;
    private final PostModerationRemovalRepository postModerationRemovalRepository;
    private final ReportRepository reportRepository;
    private final AdminMappingHelper adminMappingHelper;

    @Override
    public Page<AdminPostSummaryResponse> getPosts(String query, Long authorId, Long communityId, String contentState, Boolean reportedOnly, Pageable pageable) {
        if (authorId != null && authorId <= 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "authorId must be positive");
        }
        if (communityId != null && communityId <= 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "communityId must be positive");
        }
        if (contentState != null && !contentState.isBlank()) {
            String upper = contentState.trim().toUpperCase();
            if (!VALID_CONTENT_STATES.contains(upper)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid contentState: '" + contentState + "'. Allowed: " + VALID_CONTENT_STATES);
            }
        }

        Specification<Post> spec = (root, qSpec, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (authorId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), authorId));
            }
            if (communityId != null) {
                predicates.add(cb.equal(root.get("community").get("id"), communityId));
            }
            if (contentState != null && !contentState.isBlank()) {
                String upper = contentState.trim().toUpperCase();
                if ("ACTIVE".equals(upper)) {
                    predicates.add(cb.isNull(root.get("deletedAt")));
                } else if ("ADMIN_REMOVED".equals(upper)) {
                    predicates.add(cb.isNotNull(root.get("deletedAt")));
                    Subquery<Integer> removalSubquery = qSpec.subquery(Integer.class);
                    Root<PostModerationRemoval> removalRoot = removalSubquery.from(PostModerationRemoval.class);
                    removalSubquery.select(cb.literal(1))
                            .where(
                                    cb.equal(removalRoot.get("post").get("id"), root.get("id")),
                                    cb.isNull(removalRoot.get("restoredAt"))
                            );
                    predicates.add(cb.exists(removalSubquery));
                } else if ("AUTHOR_DELETED".equals(upper)) {
                    predicates.add(cb.isNotNull(root.get("deletedAt")));
                    Subquery<Integer> removalSubquery = qSpec.subquery(Integer.class);
                    Root<PostModerationRemoval> removalRoot = removalSubquery.from(PostModerationRemoval.class);
                    removalSubquery.select(cb.literal(1))
                            .where(
                                    cb.equal(removalRoot.get("post").get("id"), root.get("id")),
                                    cb.isNull(removalRoot.get("restoredAt"))
                            );
                    predicates.add(cb.not(cb.exists(removalSubquery)));
                }
            }
            if (Boolean.TRUE.equals(reportedOnly)) {
                Subquery<Integer> reportSubquery = qSpec.subquery(Integer.class);
                Root<Report> reportRoot = reportSubquery.from(Report.class);
                reportSubquery.select(cb.literal(1))
                        .where(cb.equal(reportRoot.get("reportedPostId"), root.get("id")));
                predicates.add(cb.exists(reportSubquery));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                Predicate contentMatches = cb.like(cb.lower(root.get("content")), pattern);

                // Content search must NOT match restricted content
                Predicate isPublic = cb.equal(root.get("visibility"), PostVisibility.PUBLIC);
                Predicate authorNotPrivate = cb.isFalse(root.get("user").get("privateAccount"));
                Join<Post, Community> communityJoin = root.join("community", JoinType.LEFT);
                Predicate communityNotPrivate = cb.or(
                        cb.isNull(root.get("community")),
                        cb.isFalse(communityJoin.get("privateCommunity"))
                );

                predicates.add(cb.and(contentMatches, isPublic, authorNotPrivate, communityNotPrivate));
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

        boolean restricted = isRestricted(post);
        boolean canRemove = !restricted && "ACTIVE".equals(contentState);
        boolean canRestore = !restricted && "ADMIN_REMOVED".equals(contentState);
        String displayContent = restricted ? "[REDACTED - RESTRICTED CONTENT]" : post.getContent();

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
                displayContent,
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

        boolean restricted = isRestricted(post);
        boolean canRemove = !restricted && "ACTIVE".equals(contentState);
        boolean canRestore = !restricted && "ADMIN_REMOVED".equals(contentState);

        long reportedCount = reportRepository.countByReportedPostId(post.getId());

        String excerpt = restricted
                ? "[REDACTED - RESTRICTED CONTENT]"
                : (post.getContent() != null
                        ? (post.getContent().length() > 100 ? post.getContent().substring(0, 100) + "..." : post.getContent())
                        : "");

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

    private boolean isRestricted(Post post) {
        if (post == null) {
            return false;
        }
        if (post.getVisibility() != PostVisibility.PUBLIC) {
            return true;
        }
        if (post.getUser() != null && post.getUser().isPrivateAccount()) {
            return true;
        }
        if (post.getCommunity() != null && post.getCommunity().isPrivateCommunity()) {
            return true;
        }
        return false;
    }
}
