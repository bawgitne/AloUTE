package com.aloute.dto.request;

import com.aloute.entity.ConversationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {

    private ConversationType type;

    private List<Long> memberIds;

    private Long targetUserId;

    private String username;

    private String name;
}
