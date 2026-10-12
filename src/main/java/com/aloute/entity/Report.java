package com.aloute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "reports")
@Getter
@Setter
public class Report {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reports_reporter"))
    private User reporter;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_user_id", foreignKey = @ForeignKey(name = "fk_reports_reported_user"))
    private User reportedUser;
    @Column(name = "reported_post_id")
    private Long reportedPostId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private ReportReason reason;
    @Column(length = 2000)
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private ReportStatus status = ReportStatus.PENDING;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist void prePersist() { if (status == null) status = ReportStatus.PENDING; if (createdAt == null) createdAt = Instant.now(); }
}
