package com.aloute.dto.request;

import com.aloute.entity.ConversationType;
import jakarta.validation.constraints.Size;
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
    @Size(max = 200, message = "A conversation cannot have more than 200 invited members")
    private List<Long> memberIds;
    private Long targetUserId;
    private String username;
    private String name;
}
