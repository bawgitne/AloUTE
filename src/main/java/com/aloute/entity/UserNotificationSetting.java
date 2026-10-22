package com.aloute.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_notification_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationSetting {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "likes_enabled", nullable = false)
    @Builder.Default
    private boolean likesEnabled = true;

    @Column(name = "comments_enabled", nullable = false)
    @Builder.Default
    private boolean commentsEnabled = true;

    @Column(name = "follows_enabled", nullable = false)
    @Builder.Default
    private boolean followsEnabled = true;

    @Column(name = "mentions_enabled", nullable = false)
    @Builder.Default
    private boolean mentionsEnabled = true;

    @Column(name = "email_notifications_enabled", nullable = false)
    @Builder.Default
    private boolean emailNotificationsEnabled = true;

    @Column(name = "sound_enabled", nullable = false)
    @Builder.Default
    private boolean soundEnabled = true;
}
