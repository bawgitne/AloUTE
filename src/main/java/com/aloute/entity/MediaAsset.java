package com.aloute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "media_assets")
@Getter
@Setter
public class MediaAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_media_owner"))
    private User owner;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MediaType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MediaPurpose purpose;
    @Column(nullable = false, length = 2048)
    private String url;
    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;
    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;
    @Column(name = "file_size", nullable = false)
    private long fileSize;
    @Column
    private Integer width;
    @Column
    private Integer height;
    @Column(name = "duration_seconds")
    private Double durationSeconds;
    @Column(name = "attached_at")
    private Instant attachedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist void prePersist() { createdAt = Instant.now(); }
}
