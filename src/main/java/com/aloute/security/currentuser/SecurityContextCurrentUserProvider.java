package com.aloute.security.currentuser;

import com.aloute.common.exception.ApiException;
import com.aloute.security.principal.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Long requireUserId() {
        return requirePrincipal().getId();
    }

    @Override
    public UserPrincipal requirePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHENTICATED", "Authentication required");
        }
        return principal;
    }
}
