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

    /**
     * Official warning of another account. Purely advisory: it never changes the
     * account's active state or permissions. Records a USER_WARN audit action and
     * delivers a mandatory ADMIN_WARN notification to the warned account.
     */
    AdminUserModerationActionResponse warnUser(Long adminId, Long userId, AdminUserModerationRequest request);

    /**
     * Resets the display profile of another account: displayName back to the
     * username and bio / website / avatar / cover cleared. Identity, credentials,
     * role, active state, sessions and content are untouched, no storage file is
     * deleted, and a USER_PROFILE_RESET audit action records the reset scope.
     */
    AdminUserModerationActionResponse resetUserProfile(Long adminId, Long userId, AdminUserModerationRequest request);

    /**
     * Changes the global role of another account between USER and SYSTEM_ADMIN.
     * Refused for the acting admin themself, for inactive targets, for no-op
     * changes, and — under the shared database lock — for demoting the last
     * active SYSTEM_ADMIN. Bumps authVersion so outstanding tokens lose or gain
     * the role only after re-authentication, and records a USER_ROLE_CHANGE audit
     * action carrying the old and new role.
     */
    AdminUserModerationActionResponse changeGlobalRole(Long adminId, Long userId,
                                                       com.aloute.dto.request.admin.AdminUserRoleChangeRequest request);
}
