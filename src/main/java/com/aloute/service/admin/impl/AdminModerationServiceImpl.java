package com.aloute.service.admin.impl;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.dto.request.admin.AdminRemovePostRequest;
import com.aloute.dto.request.admin.AdminRestorePostRequest;
import com.aloute.dto.response.admin.ModerationActionDetailResponse;
import com.aloute.dto.response.admin.ModerationActionResponse;
import com.aloute.dto.response.admin.PostRemovalActionResponse;
import com.aloute.dto.response.admin.PostRestoreActionResponse;
import com.aloute.entity.*;
import com.aloute.security.authorization.Role;
import com.aloute.repository.ModerationActionRepository;
import com.aloute.repository.PostModerationRemovalRepository;
import com.aloute.repository.PostRepository;
import com.aloute.repository.ReportRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.admin.AdminMappingHelper;
import com.aloute.service.admin.AdminModerationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminModerationServiceImpl implements AdminModerationService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final PostModerationRemovalRepository postModerationRemovalRepository;
    private final AdminMappingHelper adminMappingHelper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PostRemovalActionResponse removePost(Long actorAdminId, Long postId, AdminRemovePostRequest request) {
        User actor = userRepository.findById(actorAdminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (!actor.isActive()) {
            throw new ApiException(ErrorCode.USER_INACTIVE);
        }
        if (actor.getRole() != Role.SYSTEM_ADMIN) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }

        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        if (request.relatedReportId() != null) {
            Report relatedReport = reportRepository.findById(request.relatedReportId())
                    .orElseThrow(() -> new ApiException(ErrorCode.REPORT_NOT_FOUND, "Related report not found"));
            if (relatedReport.getReportedPostId() == null || !relatedReport.getReportedPostId().equals(postId)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Related report does not target post #" + postId);
            }
        }

        if (post.getVisibility() != PostVisibility.PUBLIC
                || (post.getUser() != null && post.getUser().isPrivateAccount())
                || (post.getCommunity() != null && post.getCommunity().isPrivateCommunity())) {
            throw new ApiException(ErrorCode.POST_ACCESS_DENIED, "Cannot moderate restricted-visibility post under current privacy policy");
        }

        if (post.getDeletedAt() != null) {
            boolean activeRemovalExists = postModerationRemovalRepository.existsByPostIdAndRestoredAtIsNull(postId);
            if (activeRemovalExists) {
                throw new ApiException(ErrorCode.CONFLICT, "Post has already been removed by moderation");
            } else {
                throw new ApiException(ErrorCode.POST_ALREADY_DELETED, "Post was already deleted by author");
            }
        }

        Instant now = Instant.now();
        post.setDeletedAt(now);

        ModerationAction action = new ModerationAction();
        action.setActor(actor);
        action.setTargetType(ModerationTargetType.POST);
        action.setTargetId(postId);
        action.setActionType("POST_REMOVE");
        action.setReasonCode(request.reasonCode());
        action.setReasonDetail(request.reasonDetail());
        action.setRelatedReportId(request.relatedReportId());

        Map<String, Object> metaMap = new HashMap<>();
        if (post.getUser() != null) {
            metaMap.put("authorId", post.getUser().getId());
            metaMap.put("authorUsername", post.getUser().getUsername());
        }
        if (request.publicNotice() != null && !request.publicNotice().isBlank()) {
            metaMap.put("publicNotice", request.publicNotice());
        }
        try {
            action.setMetadata(objectMapper.writeValueAsString(metaMap));
        } catch (Exception e) {
            log.warn("Failed to serialize moderation metadata", e);
        }

        ModerationAction savedAction = moderationActionRepository.save(action);

        PostModerationRemoval removal = new PostModerationRemoval();
        removal.setPost(post);
        removal.setRemoveAction(savedAction);
        removal.setRemovedAt(now);

        PostModerationRemoval savedRemoval = postModerationRemovalRepository.save(removal);
        postRepository.save(post);

        return new PostRemovalActionResponse(
                savedAction.getId(),
                postId,
                "POST_REMOVE",
                "ADMIN_REMOVED",
                savedRemoval.getId(),
                now
        );
    }

    @Override
    @Transactional
    public PostRestoreActionResponse restorePost(Long actorAdminId, Long postId, AdminRestorePostRequest request) {
        User actor = userRepository.findById(actorAdminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (!actor.isActive()) {
            throw new ApiException(ErrorCode.USER_INACTIVE);
        }
        if (actor.getRole() != Role.SYSTEM_ADMIN) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }

        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        if (post.getVisibility() != PostVisibility.PUBLIC
                || (post.getUser() != null && post.getUser().isPrivateAccount())
                || (post.getCommunity() != null && post.getCommunity().isPrivateCommunity())) {
            throw new ApiException(ErrorCode.POST_ACCESS_DENIED, "Cannot moderate restricted-visibility post under current privacy policy");
        }

        PostModerationRemoval activeRemoval = postModerationRemovalRepository
                .findFirstByPostIdAndRestoredAtIsNullOrderByIdDesc(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_REMOVAL_NOT_FOUND, "No active moderation removal record found for this post"));

        if (!activeRemoval.getId().equals(request.expectedRemovalId())) {
            throw new ApiException(ErrorCode.POST_RESTORE_NOT_ALLOWED, "Removal record ID mismatch");
        }

        if (post.getDeletedAt() == null) {
            throw new ApiException(ErrorCode.POST_RESTORE_NOT_ALLOWED, "Post is already active");
        }

        Instant now = Instant.now();
        post.setDeletedAt(null);

        ModerationAction restoreAction = new ModerationAction();
        restoreAction.setActor(actor);
        restoreAction.setTargetType(ModerationTargetType.POST);
        restoreAction.setTargetId(postId);
        restoreAction.setActionType("POST_RESTORE");
        restoreAction.setReasonCode(activeRemoval.getRemoveAction().getReasonCode());
        restoreAction.setReasonDetail(request.reasonDetail());
        restoreAction.setRelatedReportId(activeRemoval.getRemoveAction().getRelatedReportId());

        ModerationAction savedAction = moderationActionRepository.save(restoreAction);

        activeRemoval.setRestoreAction(savedAction);
        activeRemoval.setRestoredAt(now);
        postModerationRemovalRepository.save(activeRemoval);
        postRepository.save(post);

        return new PostRestoreActionResponse(
                savedAction.getId(),
                postId,
                "POST_RESTORE",
                "ACTIVE",
                activeRemoval.getId(),
                now
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ModerationActionResponse> getModerationActions(Long actorId, ModerationTargetType targetType, Long targetId, String actionType, Pageable pageable) {
        Specification<ModerationAction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actor").get("id"), actorId));
            }
            if (targetType != null) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (targetId != null) {
                predicates.add(cb.equal(root.get("targetId"), targetId));
            }
            if (actionType != null && !actionType.isBlank()) {
                predicates.add(cb.equal(root.get("actionType"), actionType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return moderationActionRepository.findAll(spec, pageable)
                .map(this::toModerationActionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ModerationActionDetailResponse getModerationActionDetail(Long actionId) {
        ModerationAction action = moderationActionRepository.findById(actionId)
                .orElseThrow(() -> new ApiException(ErrorCode.MODERATION_ACTION_NOT_FOUND, "Moderation action not found"));

        Map<String, Object> metaMap = null;
        if (action.getMetadata() != null && !action.getMetadata().isBlank()) {
            try {
                metaMap = objectMapper.readValue(action.getMetadata(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize moderation metadata for action {}", actionId, e);
            }
        }

        return new ModerationActionDetailResponse(
                action.getId(),
                adminMappingHelper.toUserSummary(action.getActor()),
                action.getTargetType(),
                action.getTargetId(),
                action.getActionType(),
                action.getReasonCode(),
                action.getReasonDetail(),
                action.getRelatedReportId(),
                action.getCreatedAt(),
                metaMap
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ModerationActionResponse> getPostModerationActions(Long postId, Pageable pageable) {
        return moderationActionRepository.findByTargetTypeAndTargetId(ModerationTargetType.POST, postId, pageable)
                .map(this::toModerationActionResponse);
    }

    private ModerationActionResponse toModerationActionResponse(ModerationAction action) {
        return new ModerationActionResponse(
                action.getId(),
                adminMappingHelper.toUserSummary(action.getActor()),
                action.getTargetType(),
                action.getTargetId(),
                action.getActionType(),
                action.getReasonCode(),
                action.getReasonDetail(),
                action.getRelatedReportId(),
                action.getCreatedAt()
        );
    }
}
