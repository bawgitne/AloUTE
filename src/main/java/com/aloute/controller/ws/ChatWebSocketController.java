package com.aloute.controller.ws;

import com.aloute.dto.request.SendMessageRequest;
import com.aloute.entity.ConversationMember;
import com.aloute.repository.ConversationMemberRepository;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
public class ChatWebSocketController {

    private final ConversationService conversationService;
    private final ConversationMemberRepository conversationMemberRepository;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    public ChatWebSocketController(ConversationService conversationService,
                                   ConversationMemberRepository conversationMemberRepository) {
        this.conversationService = conversationService;
        this.conversationMemberRepository = conversationMemberRepository;
    }

    public ChatWebSocketController(ConversationService conversationService,
                                   ConversationMemberRepository conversationMemberRepository,
                                   SimpMessagingTemplate messagingTemplate) {
        this.conversationService = conversationService;
        this.conversationMemberRepository = conversationMemberRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send/{conversationId}")
    public void handleChatMessage(
            @DestinationVariable Long conversationId,
            @Payload SendMessageRequest request,
            Principal principal
    ) {
        Long userId = extractUserId(principal);
        if (userId == null) {
            log.warn("Unauthenticated WebSocket message rejected");
            return;
        }
        if (conversationId == null || !conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            log.warn("User {} is not a member of conversation {} - message rejected", userId, conversationId);
            return;
        }
        conversationService.sendMessage(conversationId, userId, request);
    }

    @MessageMapping("/chat.typing/{conversationId}")
    public void handleTypingIndicator(
            @DestinationVariable Long conversationId,
            Principal principal
    ) {
        Long userId = extractUserId(principal);
        if (userId == null || messagingTemplate == null || conversationId == null) return;
        if (!conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            log.warn("User {} is not a member of conversation {} - typing indicator rejected", userId, conversationId);
            return;
        }
        try {
            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
            for (ConversationMember m : members) {
                if (m.getUser() != null && m.getUser().getId() != null && !m.getUser().getId().equals(userId)) {
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
        Long userId = extractUserId(principal);
        if (userId == null || messagingTemplate == null || conversationId == null) return;
        if (!conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            log.warn("User {} is not a member of conversation {} - read receipt rejected", userId, conversationId);
            return;
        }
        try {
            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
            for (ConversationMember m : members) {
                if (m.getUser() != null && m.getUser().getId() != null && !m.getUser().getId().equals(userId)) {
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

    private Long extractUserId(Principal principal) {
        if (principal instanceof Authentication auth && auth.isAuthenticated() && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        return null;
    }
}
