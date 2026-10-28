package com.aloute.bang;

import com.aloute.common.exception.ApiException;
import com.aloute.dto.request.CreateConversationRequest;
import com.aloute.dto.request.SendMessageRequest;
import com.aloute.dto.response.ConversationResponse;
import com.aloute.dto.response.MessageResponse;
import com.aloute.dto.response.NotificationResponse;
import com.aloute.dto.response.UnreadCountResponse;
import com.aloute.entity.*;
import com.aloute.repository.*;
import com.aloute.service.ConversationService;
import com.aloute.service.NotificationService;
import com.aloute.service.impl.ConversationServiceImpl;
import com.aloute.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagingAndNotificationServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationMemberRepository conversationMemberRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private com.aloute.repository.BlockRepository blockRepository;
    @Mock private NotificationRepository notificationRepository;

    private ConversationService conversationService;
    private NotificationService notificationService;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationServiceImpl(
                conversationRepository,
                conversationMemberRepository,
                messageRepository,
                userRepository,
                profileRepository,
                blockRepository
        );

        notificationService = new NotificationServiceImpl(
                notificationRepository,
                userRepository,
                profileRepository
        );

        user1 = new User();
        user1.setUsername("bang_lead");
        user1.setEmail("bang@aloute.local");

        user2 = new User();
        user2.setUsername("khoi_member");
        user2.setEmail("khoi@aloute.local");
    }

    @Test
    @DisplayName("TC_CONV_03: Ngăn chặn tạo cuộc trò chuyện DIRECT với chính mình")
    void cannotCreateDirectConversationWithSelf() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        CreateConversationRequest request = CreateConversationRequest.builder()
                .type(ConversationType.DIRECT)
                .targetUserId(1L)
                .build();

        assertThatThrownBy(() -> conversationService.createConversation(1L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("VALIDATION_FAILED");
                });
    }

    @Test
    @DisplayName("TC_CONV_02: Trả về cuộc trò chuyện có sẵn nếu đã tồn tại DIRECT conversation (Idempotency)")
    void returnExistingDirectConversation() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        Conversation existing = Conversation.builder()
                .id(99L)
                .type(ConversationType.DIRECT)
                .members(new ArrayList<>())
                .createdAt(Instant.now())
                .build();

        when(conversationRepository.findDirectConversationBetween(1L, 2L))
                .thenReturn(Optional.of(existing));

        CreateConversationRequest request = CreateConversationRequest.builder()
                .type(ConversationType.DIRECT)
                .targetUserId(2L)
                .build();

        ConversationResponse response = conversationService.createConversation(1L, request);
        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getType()).isEqualTo(ConversationType.DIRECT);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC_MSG_03: Từ chối gửi tin nhắn nếu người dùng không phải thành viên cuộc trò chuyện")
    void cannotSendMessageIfNotMember() {
        when(conversationMemberRepository.existsByConversationIdAndUserId(10L, 999L)).thenReturn(false);

        SendMessageRequest request = SendMessageRequest.builder()
                .content("Hello unauthorized")
                .build();

        assertThatThrownBy(() -> conversationService.sendMessage(10L, 999L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo("FORBIDDEN");
                });
    }

    @Test
    @DisplayName("TC_NOTIF_02: Bỏ qua không tạo thông báo khi tự tương tác với chính mình")
    void ignoreSelfNotification() {
        notificationService.createNotification(1L, 1L, "POST_LIKE", "POST", 100L, "Preview");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC_NOTIF_01: Tạo thông báo thành công cho người khác (Persist-Before-Publish)")
    void createNotificationForOtherUser() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        Notification mockSaved = Notification.builder()
                .id(50L)
                .recipient(user2)
                .actor(user1)
                .type("POST_LIKE")
                .targetType("POST")
                .targetId(100L)
                .contentPreview("Preview")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(mockSaved);

        notificationService.createNotification(2L, 1L, "POST_LIKE", "POST", 100L, "Preview");
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("TC_NOTIF_03: Lấy số lượng thông báo chưa đọc chính xác")
    void getUnreadCountCorrectly() {
        when(notificationRepository.countByRecipientIdAndIsReadFalse(1L)).thenReturn(5L);

        UnreadCountResponse response = notificationService.getUnreadCount(1L);
        assertThat(response.getUnreadCount()).isEqualTo(5L);
    }
}
