package com.aloute.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "post_topics", uniqueConstraints = {
        @UniqueConstraint(name = "uk_post_topics_post_topic", columnNames = {"post_id", "topic_id"})
})
public class PostTopic {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, foreignKey = @ForeignKey(name = "fk_post_topics_post"))
    private Post post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false, foreignKey = @ForeignKey(name = "fk_post_topics_topic"))
    private Topic topic;
    public Long getId() { return id; }
    public Post getPost() { return post; }
    public void setPost(Post post) { this.post = post; }
    public Topic getTopic() { return topic; }
    public void setTopic(Topic topic) { this.topic = topic; }
}
