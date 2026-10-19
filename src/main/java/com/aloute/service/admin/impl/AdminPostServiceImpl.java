package com.aloute.service.admin.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.response.admin.AdminPostDetailResponse;
import com.aloute.dto.response.admin.AdminPostSummaryResponse;
import com.aloute.entity.Community;
import com.aloute.entity.FollowId;
import com.aloute.entity.Post;
import com.aloute.entity.PostModerationRemoval;
import com.aloute.entity.PostVisibility;
import com.aloute.entity.Report;
import com.aloute.repository.BlockRepository;
import com.aloute.repository.CommunityMemberRepository;
import com.aloute.repository.FollowRepository;
import com.aloute.repository.PostModerationRemovalRepository;
import com.aloute.repository.PostRepository;
import com.aloute.repository.PostSpecifications;
import com.aloute.repository.ReportRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.PostService;
import com.aloute.service.admin.AdminMappingHelper;
import com.aloute.service.admin.AdminPostService;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AdminPostServiceImpl implements AdminPostService {

    private static final Set<String> VALID_CONTENT_STATES = Set.of("ACTIVE", "AUTHOR_DELETED", "ADMIN_REMOVED");

    private final PostRepository postRepository;
    private final PostModerationRemovalRepository postModerationRemovalRepository;
    private final ReportRepository reportRepository;
    private final AdminMappingHelper adminMappingHelper;
    private final BlockRepository blockRepository;
    private final FollowRepository followRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final PostService postService;

    @Autowired
    public AdminPostServiceImpl(
            PostRepository postRepository,
            PostModerationRemovalRepository postModerationRemovalRepository,
            ReportRepository reportRepository,
            AdminMappingHelper adminMappingHelper,
            BlockRepository blockRepository,
            FollowRepository followRepository,
            CommunityMemberRepository communityMemberRepository,
            CurrentUserProvider currentUserProvider,
            UserRepository userRepository,
            PostService postService
    ) {
        this.postRepository = postRepository;
        this.postModerationRemovalRepository = postModerationRemovalRepository;
        this.reportRepository = reportRepository;
        this.adminMappingHelper = adminMappingHelper;
        this.blockRepository = blockRepository;
        this.followRepository = followRepository;
        this.communityMemberRepository = communityMemberRepository;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.postService = postService;
    }

    public AdminPostServiceImpl(
            PostRepository postRepository,
            PostModerationRemovalRepository postModerationRemovalRepository,
            ReportRepository reportRepository,
            AdminMappingHelper adminMappingHelper,
            BlockRepository blockRepository,
            FollowRepository followRepository,
            CommunityMemberRepository communityMemberRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this(postRepository, postModerationRemovalRepository, reportRepository, adminMappingHelper,
                blockRepository, followRepository, communityMemberRepository, currentUserProvider, null, null);
    }

    public AdminPostServiceImpl(
            PostRepository postRepository,
            PostModerationRemovalRepository postModerationRemovalRepository,
            ReportRepository reportRepository,
            AdminMappingHelper adminMappingHelper
    ) {
        this(postRepository, postModerationRemovalRepository, reportRepository, adminMappingHelper, null, null, null, null, null, null);
    }

    @Override
    public Page<AdminPostSummaryResponse> getPosts(String query, Long authorId, Long communityId, String contentState, Boolean reportedOnly, Pageable pageable) {
        Long adminId = currentUserProvider != null ? currentUserProvider.getCurrentUserId().orElse(null) : null;
        return getPosts(adminId, query, authorId, communityId, contentState, reportedOnly, pageable);
    }

    @Override
    public Page<AdminPostSummaryResponse> getPosts(Long viewerAdminId, String query, Long authorId, Long communityId, String contentState, Boolean reportedOnly, Pageable pageable) {
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

        Long effectiveAdminId = viewerAdminId != null
                ? viewerAdminId
                : (currentUserProvider != null ? currentUserProvider.getCurrentUserId().orElse(null) : null);

        Specification<Post> audienceSpec = buildAudienceSpec(effectiveAdminId);

        Specification<Post> filterSpec = (root, qSpec, cb) -> {
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
                predicates.add(cb.like(cb.lower(root.get("content")), pattern));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Specification<Post> combinedSpec = Specification.where(audienceSpec).and(filterSpec);

        return postRepository.findAll(combinedSpec, pageable)
                .map(post -> toSummaryResponse(post, effectiveAdminId));
    }

    @Override
    public AdminPostDetailResponse getPostDetail(Long postId) {
        Long adminId = currentUserProvider != null ? currentUserProvider.getCurrentUserId().orElse(null) : null;
        return getPostDetail(adminId, postId);
    }

    @Override
    public AdminPostDetailResponse getPostDetail(Long viewerAdminId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND, "Post not found"));

        Long effectiveAdminId = viewerAdminId != null
                ? viewerAdminId
                : (currentUserProvider != null ? currentUserProvider.getCurrentUserId().orElse(null) : null);

        if (!isPostAudienceVisibleTo(post, effectiveAdminId)) {
            throw new ApiException(ErrorCode.POST_ACCESS_DENIED, "Access denied to restricted post");
        }

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

        List<Long> relatedReportIds = reportRepository.findByReportedPostId(postId)
                .stream()
                .map(Report::getId)
                .toList();

        Long parentPostId = visiblePostId(post.getParentPost(), effectiveAdminId);
        Long quotedPostId = visiblePostId(post.getQuotedPost(), effectiveAdminId);
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

    private Specification<Post> buildAudienceSpec(Long viewerId) {
        List<Long> blockedUserIds = new ArrayList<>();
        List<Long> followingUserIds = new ArrayList<>();
        List<Long> userCommunityIds = new ArrayList<>();

        if (viewerId != null) {
            if (blockRepository != null) {
                blockedUserIds.addAll(blockRepository.findByBlockerId(viewerId).stream().map(b -> b.getBlocked().getId()).toList());
                blockedUserIds.addAll(blockRepository.findByBlockedId(viewerId).stream().map(b -> b.getBlocker().getId()).toList());
            }
            if (followRepository != null) {
                followingUserIds.addAll(followRepository.findByFollower_Id(viewerId).stream().map(f -> f.getFollowing().getId()).toList());
            }
            if (communityMemberRepository != null) {
                userCommunityIds.addAll(communityMemberRepository.findByUserId(viewerId).stream().map(cm -> cm.getCommunity().getId()).toList());
            }
        }

        return PostSpecifications.visibleAudienceTo(viewerId, blockedUserIds, followingUserIds, userCommunityIds);
    }

    private boolean isPostAudienceVisibleTo(Post post, Long viewerId) {
        if (post == null) {
            return false;
        }

        // Soft-deleted Communities are not readable through any normal Post path.
        if (post.getCommunity() != null && post.getCommunity().getDeletedAt() != null) {
            return false;
        }

        // 1. Canonical private-community invariant: non-members cannot access private community posts.
        if (post.getCommunity() != null && post.getCommunity().isPrivateCommunity()) {
            if (viewerId == null) {
                return false;
            }
            boolean isOwner = post.getCommunity().getOwner() != null
                    && Objects.equals(post.getCommunity().getOwner().getId(), viewerId);
            boolean isMember = communityMemberRepository != null
                    && communityMemberRepository.existsByCommunityIdAndUserId(post.getCommunity().getId(), viewerId);
            if (!isOwner && !isMember) {
                return false;
            }
        }

        Long authorId = post.getUser() != null ? post.getUser().getId() : null;

        // 2. Check mutual block relationship
        if (viewerId != null && authorId != null && blockRepository != null) {
            if (blockRepository.existsByBlockerIdAndBlockedId(viewerId, authorId)
                    || blockRepository.existsByBlockerIdAndBlockedId(authorId, viewerId)) {
                return false;
            }
        }

        // 3. Author audience exception: author can view their own post if community gate and blocks allow
        if (viewerId != null && authorId != null && Objects.equals(authorId, viewerId)) {
            return true;
        }

        // 4. Guest viewer
        if (viewerId == null) {
            if (post.getUser() != null && post.getUser().isPrivateAccount()) {
                return false;
            }
            return post.getVisibility() == PostVisibility.PUBLIC;
        }

        // 5. Authenticated non-author viewer: check author account privacy
        boolean isFollowingAuthor = false;
        if (authorId != null && followRepository != null) {
            isFollowingAuthor = followRepository.existsById(new FollowId(viewerId, authorId));
        }

        if (post.getUser() != null && post.getUser().isPrivateAccount() && !isFollowingAuthor) {
            return false;
        }

        // 6. Check post visibility enum
        if (post.getVisibility() == PostVisibility.PUBLIC) {
            return true;
        }
        if (post.getVisibility() == PostVisibility.FOLLOWERS) {
            return isFollowingAuthor;
        }

        return false;
    }

    private Long visiblePostId(Post targetPost, Long viewerId) {
        if (targetPost == null || targetPost.getDeletedAt() != null) {
            return null;
        }
        if (postService == null || !postService.isPostVisibleTo(targetPost, viewerId)) {
            return null;
        }
        return targetPost.getId();
    }

    private AdminPostSummaryResponse toSummaryResponse(Post post) {
        Long adminId = currentUserProvider != null ? currentUserProvider.getCurrentUserId().orElse(null) : null;
        return toSummaryResponse(post, adminId);
    }

    private AdminPostSummaryResponse toSummaryResponse(Post post, Long viewerId) {
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

        String excerpt = post.getContent() != null
                ? (post.getContent().length() > 100 ? post.getContent().substring(0, 100) + "..." : post.getContent())
                : "";

        Long parentPostId = visiblePostId(post.getParentPost(), viewerId);
        Long quotedPostId = visiblePostId(post.getQuotedPost(), viewerId);
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

    @Override
    public Page<AdminPostSummaryResponse> getUserPosts(Long userId, String contentState, Pageable pageable, Long viewerId) {
        if (userId == null || userId <= 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "userId must be positive");
        }
        if (userRepository != null) {
            userRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found"));
        }

        if (contentState != null && !contentState.isBlank()) {
            String upper = contentState.trim().toUpperCase(Locale.ROOT);
            if (!VALID_CONTENT_STATES.contains(upper)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid contentState: '" + contentState + "'. Allowed: " + VALID_CONTENT_STATES);
            }
        }

        Specification<Post> spec = (root, qSpec, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            predicates.add(cb.isNull(root.get("parentPost")));

            if (contentState != null && !contentState.isBlank()) {
                String upper = contentState.trim().toUpperCase(Locale.ROOT);
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
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        if (postService != null) {
            spec = spec.and(postService.buildVisibilitySpec(viewerId));
        }

        Pageable effectivePageable = pageable;
        if (pageable.getSort().isUnsorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        return postRepository.findAll(spec, effectivePageable)
                .map(post -> toUserPostSummaryResponse(post, viewerId));
    }

    private AdminPostSummaryResponse toUserPostSummaryResponse(Post post, Long viewerId) {
        return toSummaryResponse(post, viewerId);
    }
}
