package com.aloute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "post_media", uniqueConstraints = {
        @UniqueConstraint(name = "uk_post_media_media", columnNames = "media_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PostMedia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, foreignKey = @ForeignKey(name = "fk_post_media_post"))
    private Post post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false, foreignKey = @ForeignKey(name = "fk_post_media_media"))
    private MediaAsset media;
    @Column(nullable = false)
    private int position;

    public PostMedia(Post post, MediaAsset media, int position) {
        this.post = post;
        this.media = media;
        this.position = position;
    }
}
