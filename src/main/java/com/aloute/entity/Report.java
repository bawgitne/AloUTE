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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id", foreignKey = @ForeignKey(name = "fk_reports_reviewed_by"))
    private User reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist void prePersist() {
        if (status == null) status = ReportStatus.PENDING;
        if (createdAt == null) createdAt = Instant.now();
        if (version == null) version = 0L;
    }
}

