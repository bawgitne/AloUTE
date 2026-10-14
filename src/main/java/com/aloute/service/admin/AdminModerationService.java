package com.aloute.service.admin;

import com.aloute.dto.request.admin.AdminRemovePostRequest;
import com.aloute.dto.request.admin.AdminRestorePostRequest;
import com.aloute.dto.response.admin.ModerationActionDetailResponse;
import com.aloute.dto.response.admin.ModerationActionResponse;
import com.aloute.dto.response.admin.PostRemovalActionResponse;
import com.aloute.dto.response.admin.PostRestoreActionResponse;
import com.aloute.entity.ModerationTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminModerationService {

    PostRemovalActionResponse removePost(Long actorAdminId, Long postId, AdminRemovePostRequest request);

    PostRestoreActionResponse restorePost(Long actorAdminId, Long postId, AdminRestorePostRequest request);

    Page<ModerationActionResponse> getModerationActions(Long actorId, ModerationTargetType targetType, Long targetId, String actionType, Pageable pageable);

    ModerationActionDetailResponse getModerationActionDetail(Long actionId);

    Page<ModerationActionResponse> getPostModerationActions(Long postId, Pageable pageable);
}
