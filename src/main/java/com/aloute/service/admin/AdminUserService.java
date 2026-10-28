package com.aloute.service.admin;

import com.aloute.dto.request.admin.AdminUserModerationRequest;
import com.aloute.dto.response.admin.AdminUserDetailResponse;
import com.aloute.dto.response.admin.AdminUserModerationActionResponse;
import com.aloute.dto.response.admin.AdminUserSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {

    Page<AdminUserSummaryResponse> getUsers(String query, String role, Boolean active, Pageable pageable);

    AdminUserDetailResponse getUserDetail(Long userId);

    /**
     * System-admin suspension of another account. Sets the target inactive,
     * revokes every existing session (authVersion bump) and records an audit
     * action. Guarded against self-suspension and against suspending the last
     * remaining SYSTEM_ADMIN.
     */
    AdminUserModerationActionResponse suspendUser(Long adminId, Long userId, AdminUserModerationRequest request);

    /**
     * System-admin reactivation of a suspended account. Restores the active
     * flag, forces a fresh sign-in (authVersion bump) and records an audit action.
     */
    AdminUserModerationActionResponse reactivateUser(Long adminId, Long userId, AdminUserModerationRequest request);
}
