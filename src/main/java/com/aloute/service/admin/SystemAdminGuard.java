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

    /**
     * Locks the SYSTEM_ADMIN set and refuses the operation when the given account is an
     * active administrator and deactivating it would leave zero active administrators.
     *
     * @return the fresh, lock-managed instance of {@code target} (or {@code target}
     *         unchanged when it is not a SYSTEM_ADMIN row)
     */
    public User lockAndRequireAdminRemains(User target) {
        if (target == null || target.getRole() != Role.SYSTEM_ADMIN) {
            return target;
        }
        List<User> admins = lockAdmins();
        long activeAdmins = admins.stream().filter(User::isActive).count();
        User fresh = admins.stream()
                .filter(u -> Objects.equals(u.getId(), target.getId()))
                .findFirst()
                .orElse(target);
        if (fresh.isActive() && activeAdmins <= 1L) {
            throw new ApiException(ErrorCode.USER_LAST_ADMIN_PROTECTED,
                    "Không thể vô hiệu hóa quản trị viên hệ thống đang hoạt động cuối cùng.");
        }
        return fresh;
    }
}
