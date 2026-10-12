package com.aloute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "post_topics", uniqueConstraints = {
        @UniqueConstraint(name = "uk_post_topics_post_topic", columnNames = {"post_id", "topic_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class PostTopic {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, foreignKey = @ForeignKey(name = "fk_post_topics_post"))
    private Post post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false, foreignKey = @ForeignKey(name = "fk_post_topics_topic"))
    private Topic topic;

    public PostTopic(Post post, Topic topic) {
        this.post = post;
        this.topic = topic;
    }
}
