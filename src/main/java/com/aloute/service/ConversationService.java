package com.aloute.service;

import com.aloute.dto.request.CreateConversationRequest;
import com.aloute.dto.request.SendMessageRequest;
import com.aloute.dto.response.ConversationResponse;
import com.aloute.dto.response.MessageResponse;
import com.aloute.entity.Conversation;
import com.aloute.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ConversationService {
    Page<ConversationResponse> getConversations(Long currentUserId, Pageable pageable);
    ConversationResponse getConversation(Long conversationId, Long currentUserId);
    ConversationResponse createConversation(Long currentUserId, CreateConversationRequest request);
    Page<MessageResponse> getMessages(Long conversationId, Long currentUserId, Pageable pageable);
    MessageResponse sendMessage(Long conversationId, Long currentUserId, SendMessageRequest request);
    void markAsRead(Long conversationId, Long currentUserId);
    ConversationResponse mapToConversationResponse(Conversation conv, Long currentUserId);
    MessageResponse mapToMessageResponse(Message msg);
}
