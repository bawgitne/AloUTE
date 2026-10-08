package com.aloute.security.currentuser;

import com.aloute.security.principal.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;

public interface CurrentUserProvider {
    Long requireUserId();
    UserPrincipal requirePrincipal();

    default Optional<Long> getCurrentUserId() {
        return getCurrentPrincipal().map(UserPrincipal::getId);
    }

    default Optional<UserPrincipal> getCurrentPrincipal() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.isAuthenticated() && a.getPrincipal() instanceof UserPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }
}
