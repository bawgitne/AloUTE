package com.aloute.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.request.CreateConversationRequest;
import com.aloute.dto.request.SendMessageRequest;
import com.aloute.dto.response.ConversationResponse;
import com.aloute.dto.response.MessageResponse;
import com.aloute.dto.response.UserSummary;
import com.aloute.entity.Conversation;
import com.aloute.entity.ConversationMember;
import com.aloute.entity.ConversationType;
import com.aloute.entity.Message;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.ConversationMemberRepository;
import com.aloute.repository.ConversationRepository;
import com.aloute.repository.MessageRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.ConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional(readOnly = true)
    public Page<ConversationResponse> getConversations(Long currentUserId, Pageable pageable) {
        return conversationRepository.findByUserId(currentUserId, pageable)
                .map(conv -> mapToConversationResponse(conv, currentUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(Long conversationId, Long currentUserId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Conversation not found"));

        validateMembership(conversationId, currentUserId);
        return mapToConversationResponse(conv, currentUserId);
    }

    @Override
    @Transactional
    public ConversationResponse createConversation(Long currentUserId, CreateConversationRequest request) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        ConversationType type = request.getType() != null ? request.getType() : ConversationType.DIRECT;
        Set<Long> memberIdSet = new HashSet<>();
        memberIdSet.add(currentUserId);

        Long targetUserId = request.getTargetUserId();
        if (targetUserId == null && request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            User target = userRepository.findByUsernameIgnoreCase(request.getUsername().trim())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Target user not found"));
            targetUserId = target.getId();
        }
        if (targetUserId == null && request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
            targetUserId = request.getMemberIds().stream()
                    .filter(id -> !id.equals(currentUserId))
                    .findFirst()
                    .orElse(request.getMemberIds().get(0));
            memberIdSet.addAll(request.getMemberIds());
        }

        if (targetUserId != null) {
            memberIdSet.add(targetUserId);
        }

        if (type == ConversationType.DIRECT && targetUserId != null) {
            if (targetUserId.equals(currentUserId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Cannot create a direct conversation with yourself");
            }
            var existing = conversationRepository.findDirectConversationBetween(currentUserId, targetUserId);
            if (existing.isPresent()) {
                return mapToConversationResponse(existing.get(), currentUserId);
            }
        }

        List<User> targetUsers = userRepository.findAllById(memberIdSet);
        if (targetUsers.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "At least one participant required");
        }

        Conversation conv = Conversation.builder()
                .type(type)
                .name(request.getName())
                .members(new ArrayList<>())
                .build();

        conv = conversationRepository.save(conv);

        for (User u : targetUsers) {
            ConversationMember member = ConversationMember.builder()
                    .conversation(conv)
                    .user(u)
                    .build();
            conv.getMembers().add(member);
        }

        conv = conversationRepository.save(conv);
        log.info("Created conversation id={} type={} members={}", conv.getId(), conv.getType(), memberIdSet.size());
        return mapToConversationResponse(conv, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(Long conversationId, Long currentUserId, Pageable pageable) {
        validateMembership(conversationId, currentUserId);
        return messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(conversationId, pageable)
                .map(this::mapToMessageResponse);
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(Long conversationId, Long currentUserId, SendMessageRequest request) {
        validateMembership(conversationId, currentUserId);

        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Conversation not found"));

        User sender = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        Message replyTo = null;
        if (request.getReplyToMessageId() != null) {
            replyTo = messageRepository.findById(request.getReplyToMessageId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "REPLY_MESSAGE_NOT_FOUND", "Target reply message not found"));
            if (!replyTo.getConversation().getId().equals(conversationId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPLY_CONVERSATION", "Target reply message belongs to a different conversation");
            }
        }

        Message msg = Message.builder()
                .conversation(conv)
                .sender(sender)
                .replyToMessage(replyTo)
                .content(request.getContent().trim())
                .build();

        msg = messageRepository.save(msg);
        conv.setUpdatedAt(Instant.now());
        conversationRepository.save(conv);

        // Update sender's last read pointer so own unread count stays 0
        final Long sentMsgId = msg.getId();
        conversationMemberRepository.findByConversationIdAndUserId(conversationId, currentUserId)
                .ifPresent(m -> {
                    m.setLastReadMessageId(sentMsgId);
                    m.setLastReadAt(Instant.now());
                    conversationMemberRepository.save(m);
                });

        MessageResponse response = mapToMessageResponse(msg);

        // Realtime STOMP transport (Persist-Before-Publish: AFTER_COMMIT)
        if (messagingTemplate != null) {
            List<Long> recipientIds = conversationMemberRepository.findByConversationId(conversationId).stream()
                    .map(m -> m.getUser().getId())
                    .filter(id -> !id.equals(currentUserId))
                    .toList();
            log.info("SendMessage: recipientIds={}", recipientIds);

            Runnable publishTask = () -> {
                for (Long recipientId : recipientIds) {
                    try {
                        log.info("Sending realtime message to user {} destination /queue/messages", recipientId);
                        messagingTemplate.convertAndSendToUser(
                                String.valueOf(recipientId),
                                "/queue/messages",
                                response
                        );
                        log.info("Successfully published realtime message to user {}", recipientId);
                    } catch (Exception ex) {
                        log.warn("Failed to publish realtime websocket message to user {}: {}", recipientId, ex.getMessage());
                    }
                }
            };

            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                publishTask.run();
                            }
                        }
                );
            } else {
                publishTask.run();
            }
        } else {
            log.warn("messagingTemplate is null in ConversationServiceImpl!");
        }

        log.info("Sent message id={} to conversation={}", msg.getId(), conversationId);
        return response;
    }

    private void validateMembership(Long conversationId, Long currentUserId) {
        if (!conversationMemberRepository.existsByConversationIdAndUserId(conversationId, currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not a member of this conversation");
        }
    }

    @Override
    @Transactional
    public void markAsRead(Long conversationId, Long currentUserId) {
        validateMembership(conversationId, currentUserId);
        ConversationMember member = conversationMemberRepository.findByConversationIdAndUserId(conversationId, currentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Conversation member not found"));

        messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversationId)
                .ifPresent(lastMsg -> {
                    member.setLastReadMessageId(lastMsg.getId());
                    member.setLastReadAt(Instant.now());
                    conversationMemberRepository.save(member);
                });
    }

    @Override
    public ConversationResponse mapToConversationResponse(Conversation conv, Long currentUserId) {
        List<UserSummary> members = conv.getMembers().stream()
                .map(m -> toUserSummary(m.getUser()))
                .collect(Collectors.toList());

        MessageResponse lastMessage = messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conv.getId())
                .map(this::mapToMessageResponse)
                .orElse(null);

        UserSummary otherParticipant = members.stream()
                .filter(m -> !m.id().equals(currentUserId))
                .findFirst()
                .orElse(null);

        int unreadCount = 0;
        if (currentUserId != null && conv.getMembers() != null) {
            ConversationMember myMember = conv.getMembers().stream()
                    .filter(m -> m.getUser() != null && m.getUser().getId().equals(currentUserId))
                    .findFirst()
                    .orElse(null);
            if (myMember != null) {
                if (myMember.getLastReadMessageId() != null) {
                    unreadCount = (int) messageRepository.countByConversationIdAndIdGreaterThan(conv.getId(), myMember.getLastReadMessageId());
                } else {
                    unreadCount = (int) messageRepository.countByConversationId(conv.getId());
                }
            }
        }

        return ConversationResponse.builder()
                .id(conv.getId())
                .type(conv.getType())
                .name(conv.getName())
                .avatarUrl(conv.getAvatarUrl())
                .members(members)
                .participant(otherParticipant)
                .otherParticipant(otherParticipant)
                .lastMessage(lastMessage)
                .unreadCount(unreadCount)
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt() != null ? conv.getUpdatedAt() : conv.getCreatedAt())
                .build();
    }

    @Override
    public MessageResponse mapToMessageResponse(Message msg) {
        return MessageResponse.builder()
                .id(msg.getId())
                .conversationId(msg.getConversation().getId())
                .sender(toUserSummary(msg.getSender()))
                .replyToMessageId(msg.getReplyToMessage() != null ? msg.getReplyToMessage().getId() : null)
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private UserSummary toUserSummary(User user) {
        if (user == null) return null;
        Profile profile = profileRepository.findById(user.getId()).orElse(null);
        String displayName = (profile != null && profile.getDisplayName() != null) ? profile.getDisplayName() : user.getUsername();
        String avatarUrl = profile != null ? profile.getAvatarUrl() : null;
        return new UserSummary(user.getId(), user.getUsername(), displayName, avatarUrl, user.isVerified());
    }
}
