package com.aloute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollResponse {
    private Long id;
    private String question;
    private boolean allowMultiple;
    private Instant closesAt;
    private Instant expiresAt;
    private int totalVotes;
    private Long userVotedOptionId;
    private List<PollOptionResponse> options;
}
