package com.aloute.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "poll_options", indexes = {
        @Index(name = "idx_poll_options_poll", columnList = "poll_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PollOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    @Column(name = "option_text", nullable = false, length = 200)
    private String optionText;

    @Builder.Default
    @Column(name = "vote_count", nullable = false)
    private int voteCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private int position = 0;
}
