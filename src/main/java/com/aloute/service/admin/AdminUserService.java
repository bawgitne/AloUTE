package com.aloute.service.admin;

import com.aloute.dto.request.admin.AdminUserModerationRequest;
import com.aloute.dto.request.admin.AdminUserProfileResetRequest;
import com.aloute.dto.request.admin.AdminUserRoleChangeRequest;
import com.aloute.dto.request.admin.AdminUserWarnRequest;
import com.aloute.dto.response.admin.AdminUserDetailResponse;
import com.aloute.dto.response.admin.AdminUserModerationActionResponse;
import com.aloute.dto.response.admin.AdminUserProfileResetResponse;
import com.aloute.dto.response.admin.AdminUserRoleChangeResponse;
import com.aloute.dto.response.admin.AdminUserSummaryResponse;
import com.aloute.dto.response.admin.AdminUserWarnResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {

    Page<AdminUserSummaryResponse> getUsers(String query, String role, Boolean active, Pageable pageable);

    AdminUserDetailResponse getUserDetail(Long userId);

    /** Viewer-aware detail: capabilities exclude actions the viewer cannot perform on this target. */
    AdminUserDetailResponse getUserDetail(Long userId, Long viewerId);

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

    /**
     * Advisory warning: records a USER_WARN audit action and persists a USER_WARN
     * notification for the target. Never changes active state, role or auth version.
     */
    AdminUserWarnResponse warnUser(Long adminId, Long userId, AdminUserWarnRequest request);

    /**
     * Confirmed reset of the visible Profile fields. Preserves identity, authority,
     * session and content data; only clears URL/text display fields.
     */
    AdminUserProfileResetResponse resetUserProfile(Long adminId, Long userId, AdminUserProfileResetRequest request);

    /**
     * Changes only the global USER/SYSTEM_ADMIN role. Enforces self/inactive/no-op
     * rules, the database-locked last-active-admin invariant, and revokes old JWTs.
     */
    AdminUserRoleChangeResponse changeUserRole(Long adminId, Long userId, AdminUserRoleChangeRequest request);
}
