package com.aloute.entity;

import com.aloute.security.authorization.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
@org.hibernate.annotations.DynamicUpdate
@Getter
@Setter
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 50)
    private String username;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(name = "is_verified", nullable = false)
    private boolean verified;
    @Column(name = "verification_required", nullable = false)
    private boolean verificationRequired = false;
    @Column(name = "is_private", nullable = false)
    private boolean privateAccount;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    @Enumerated(EnumType.STRING)
    @Column(name = "global_role", nullable = false, length = 32)
    private Role role = Role.USER;
    @Column(name = "auth_version", nullable = false)
    private Long authVersion = 0L;
    @Version
    @Column(name = "version")
    private Long version;
    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = false;

    @Column(name = "two_factor_secret", length = 255)
    private String twoFactorSecret;

    @Column(name = "two_factor_temp_secret", length = 255)
    private String twoFactorTempSecret;

    @Column(name = "two_factor_type", length = 32)
    private String twoFactorType;

    @Column(name = "two_factor_confirmed_at")
    private Instant twoFactorConfirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (role == null) role = Role.USER;
        if (authVersion == null) authVersion = 0L;
        if (twoFactorType == null) twoFactorType = "TOTP";
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
