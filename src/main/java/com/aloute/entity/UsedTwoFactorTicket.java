package com.aloute.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * BL-08: one-time store for consumed 2FA login tickets. A verified ticket is
 * recorded here before its session is minted; replaying the same ticket — even
 * within its short TTL or from another instance — is rejected via the unique
 * ticket_hash constraint. Rows are short-lived and housekept on every verify.
 */
@Entity
@Table(name = "used_two_factor_tickets",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_2fa_used_tickets_hash", columnNames = "ticket_hash")
        },
        indexes = {
                @Index(name = "idx_2fa_used_tickets_consumed_at", columnList = "consumed_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsedTwoFactorTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_hash", nullable = false, length = 64)
    private String ticketHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;
}
