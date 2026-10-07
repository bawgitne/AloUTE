package com.aloute.controller.api;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.api.PageMeta;
import com.aloute.dto.request.CreateConversationRequest;
import com.aloute.dto.request.SendMessageRequest;
import com.aloute.dto.response.ConversationResponse;
import com.aloute.dto.response.MessageResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<List<ConversationResponse>> getConversations(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long currentUserId = currentUserProvider.requireUserId();
        Page<ConversationResponse> page = conversationService.getConversations(currentUserId, pageable);
        return ApiResponse.success(page.getContent(), PageMeta.from(page));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
            @Valid @RequestBody CreateConversationRequest request
    ) {
        Long currentUserId = currentUserProvider.requireUserId();
        ConversationResponse response = conversationService.createConversation(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> getConversation(
            @PathVariable Long conversationId
    ) {
        Long currentUserId = currentUserProvider.requireUserId();
        ConversationResponse response = conversationService.getConversation(conversationId, currentUserId);
        return ApiResponse.success(response);
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<MessageResponse>> getMessages(
            @PathVariable Long conversationId,
            @PageableDefault(size = 30) Pageable pageable
    ) {
        Long currentUserId = currentUserProvider.requireUserId();
        Page<MessageResponse> page = conversationService.getMessages(conversationId, currentUserId, pageable);
        return ApiResponse.success(page.getContent(), PageMeta.from(page));
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        Long currentUserId = currentUserProvider.requireUserId();
        MessageResponse response = conversationService.sendMessage(conversationId, currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
