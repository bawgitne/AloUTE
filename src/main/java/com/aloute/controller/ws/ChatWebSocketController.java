package com.aloute.controller.ws;

import com.aloute.dto.request.SendMessageRequest;
import com.aloute.entity.ConversationMember;
import com.aloute.repository.ConversationMemberRepository;
import com.aloute.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ConversationService conversationService;
    private final ConversationMemberRepository conversationMemberRepository;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send/{conversationId}")
    public void handleChatMessage(
            @DestinationVariable Long conversationId,
            @Payload SendMessageRequest request,
            Principal principal
    ) {
        if (principal == null) {
            log.warn("Unauthenticated WebSocket message rejected");
            return;
        }
        try {
            Long userId = Long.parseLong(principal.getName());
            conversationService.sendMessage(conversationId, userId, request);
        } catch (NumberFormatException ex) {
            log.error("Invalid principal name for user ID: {}", principal.getName());
        }
    }

    @MessageMapping("/chat.typing/{conversationId}")
    public void handleTypingIndicator(
            @DestinationVariable Long conversationId,
            Principal principal
    ) {
        if (principal == null || messagingTemplate == null) return;
        try {
            Long userId = Long.parseLong(principal.getName());
            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
            for (ConversationMember m : members) {
                if (!m.getUser().getId().equals(userId)) {
                    messagingTemplate.convertAndSendToUser(
                            String.valueOf(m.getUser().getId()),
                            "/queue/typing",
                            Map.of("conversationId", conversationId, "userId", userId, "isTyping", true)
                    );
                }
            }
        } catch (Exception ex) {
            log.debug("Typing notification skipped: {}", ex.getMessage());
        }
    }

    @MessageMapping("/chat.read/{conversationId}")
    public void handleReadReceipt(
            @DestinationVariable Long conversationId,
            Principal principal
    ) {
        if (principal == null || messagingTemplate == null) return;
        try {
            Long userId = Long.parseLong(principal.getName());
            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
            for (ConversationMember m : members) {
                if (!m.getUser().getId().equals(userId)) {
                    messagingTemplate.convertAndSendToUser(
                            String.valueOf(m.getUser().getId()),
                            "/queue/read-receipts",
                            Map.of("conversationId", conversationId, "readByUserId", userId)
                    );
                }
            }
        } catch (Exception ex) {
            log.debug("Read receipt notification skipped: {}", ex.getMessage());
        }
    }
}
