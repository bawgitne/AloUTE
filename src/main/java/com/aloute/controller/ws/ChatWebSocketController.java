package com.aloute.controller.ws;

import com.aloute.dto.request.SendMessageRequest;
import com.aloute.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ConversationService conversationService;

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
}
