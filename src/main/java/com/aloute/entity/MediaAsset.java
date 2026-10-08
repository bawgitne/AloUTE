package com.aloute.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "media_assets")
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

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public MediaType getType() { return type; }
    public void setType(MediaType type) { this.type = type; }
    public MediaPurpose getPurpose() { return purpose; }
    public void setPurpose(MediaPurpose purpose) { this.purpose = purpose; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public Double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; }
    public Instant getAttachedAt() { return attachedAt; }
    public void setAttachedAt(Instant attachedAt) { this.attachedAt = attachedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
