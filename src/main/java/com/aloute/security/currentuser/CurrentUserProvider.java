package com.aloute.security.currentuser;

import com.aloute.security.principal.UserPrincipal;

public interface CurrentUserProvider {
    Long requireUserId();
    UserPrincipal requirePrincipal();
}
