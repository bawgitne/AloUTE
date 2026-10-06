package com.aloute.security;

import com.aloute.entity.User;
import com.aloute.security.authorization.GlobalAuthority;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserPrincipalAuthorizationTest {

    @Test
    void systemAdminReceivesModerationAuthorities() {
        User user = new User();
        user.setUsername("admin");
        user.setEmail("admin@example.com");
        user.setPasswordHash("encoded");
        user.setActive(true);
        user.setRole(Role.SYSTEM_ADMIN);

        UserPrincipal principal = UserPrincipal.from(user);

        assertThat(principal.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_SYSTEM_ADMIN", GlobalAuthority.REPORT_REVIEW.name(), GlobalAuthority.USER_SUSPEND.name());
    }
}
