package com.aloute.dto.response;

import com.aloute.entity.ConversationType;
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
public class ConversationResponse {

    private Long id;
    private ConversationType type;
    private String name;
    private String avatarUrl;
    private List<UserSummary> members;
    private UserSummary participant;
    private UserSummary otherParticipant;
    private MessageResponse lastMessage;
    private int unreadCount;
    private Instant createdAt;
    private Instant updatedAt;
}
