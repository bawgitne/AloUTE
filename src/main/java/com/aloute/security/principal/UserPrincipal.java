package com.aloute.security.principal;

import com.aloute.entity.User;
import com.aloute.security.authorization.GlobalAuthority;
import com.aloute.security.authorization.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UserPrincipal implements UserDetails {
    private final Long id;
    private final String username;
    private final String email;
    private final String password;
    private final boolean active;
    private final Role role;
    private final Collection<? extends GrantedAuthority> authorities;

    private UserPrincipal(Long id, String username, String email, String password,
                          boolean active, Role role, Collection<? extends GrantedAuthority> authorities) {
        this.id=id; this.username=username; this.email=email; this.password=password; this.active=active; this.role=role; this.authorities=authorities;
    }

    public static UserPrincipal from(User user) {
        Role role = user.getRole() == null ? Role.USER : user.getRole();
        List<GrantedAuthority> auth = new ArrayList<>();
        auth.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        if (role == Role.SYSTEM_ADMIN) {
            auth.add(new SimpleGrantedAuthority(GlobalAuthority.REPORT_REVIEW.name()));
            auth.add(new SimpleGrantedAuthority(GlobalAuthority.USER_SUSPEND.name()));
        }
        return new UserPrincipal(user.getId(), user.getUsername(), user.getEmail(), user.getPasswordHash(), user.isActive(), role, List.copyOf(auth));
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public String getDisplayUsername() { return username; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return active; }
}
