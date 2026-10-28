package com.aloute.service.admin;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import com.aloute.entity.User;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Single owner of the "at least one active SYSTEM_ADMIN" invariant.
 *
 * Both administrative suspension and self-service deactivation route through this
 * guard so the rule and its concurrency protection have exactly one implementation.
 *
 * Concurrency model: every caller that may reduce the active-admin count first takes
 * a pessimistic write lock on ALL SYSTEM_ADMIN rows (ordered by id, so two callers
 * line up instead of deadlocking). Only then is the active count evaluated and the
 * mutation applied, inside the caller's surrounding transaction. Because the lock is
 * taken in the database, the invariant also holds across multiple app instances — a
 * JVM {@code synchronized} block would not.
 *
 * This component must be invoked inside an existing @Transactional service method so
 * the lock and the subsequent write share one transaction.
 */
@Component
@RequiredArgsConstructor
public class SystemAdminGuard {

    private final UserRepository userRepository;

    /** Locked, id-ordered snapshot of every SYSTEM_ADMIN row (managed entities). */
    public List<User> lockAdmins() {
        return userRepository.lockByRoleOrdered(Role.SYSTEM_ADMIN);
    }

    /** Locked account-state snapshot used by suspension, reactivation and role changes. */
    public record LockedAccount(List<User> admins, User target) {
    }

    /**
     * Locks all SYSTEM_ADMIN rows first (id-ordered), then the target row. Every
     * administrative operation that can change active state or the global role uses this
     * same order, so role change, suspension, reactivation and self-deactivation cannot
     * interleave into a state with zero active administrators or an inactive new admin.
     */
    public LockedAccount lockAdminsAndTarget(Long targetId) {
        List<User> admins = lockAdmins();
        User target = admins.stream()
                .filter(u -> Objects.equals(u.getId(), targetId))
                .findFirst()
                .orElseGet(() -> userRepository.findByIdForUpdate(targetId)
                        .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found")));
        return new LockedAccount(List.copyOf(admins), target);
    }

    /**
     * Refuses an operation that would deactivate or demote the last active administrator.
     * {@code admins} is the locked, authoritative snapshot; {@code target} is expected to
     * be the fresh instance returned by {@link #lockAdminsAndTarget(Long)}.
     */
    public void requireActiveAdminRemains(User target, List<User> admins) {
        User fresh = admins.stream()
                .filter(u -> Objects.equals(u.getId(), target.getId()))
                .findFirst()
                .orElse(target);
        long activeAdmins = admins.stream().filter(User::isActive).count();
        if (fresh.getRole() == Role.SYSTEM_ADMIN && fresh.isActive() && activeAdmins <= 1L) {
            throw new ApiException(ErrorCode.USER_LAST_ADMIN_PROTECTED,
                    "Không thể vô hiệu hóa hoặc hạ quyền quản trị viên hệ thống đang hoạt động cuối cùng.");
        }
    }

}
