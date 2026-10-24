package com.aloute.integration;

import com.aloute.dto.response.MessageResponse;
import com.aloute.dto.response.NotificationResponse;
import com.aloute.entity.Conversation;
import com.aloute.entity.ConversationMember;
import com.aloute.entity.ConversationType;
import com.aloute.entity.Message;
import com.aloute.entity.Notification;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.ConversationMemberRepository;
import com.aloute.repository.ConversationRepository;
import com.aloute.repository.MessageRepository;
import com.aloute.repository.NotificationRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.ConversationService;
import com.aloute.service.NotificationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pagination stability: rows sharing a timestamp must still come back in a
 * deterministic order (created_at DESC, id DESC), so paging through a mailbox
 * or a conversation never repeats or skips rows. Both repository paths are
 * forced into a full timestamp tie with a native update, then the page order
 * is asserted twice to pin determinism.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class PaginationStabilityIntegrationTest {

    private static final Instant TIED_TIMESTAMP = Instant.parse("2026-10-24T10:00:00Z");

    @Autowired private NotificationService notificationService;
    @Autowired private ConversationService conversationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private ConversationMemberRepository conversationMemberRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private EntityManager entityManager;

    private User recipient;

    @BeforeEach
    void seedUsers() {
        recipient = newUser("pagerecipient");
    }

    private User newUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@aloute.test");
        user.setPasswordHash("$2a$10$placeholder");
        user.setVerified(true);
        user.setActive(true);
        user = userRepository.save(user);
        Profile profile = new Profile();
        profile.setUser(user);
        profile.setDisplayName(username + " display");
        profileRepository.save(profile);
        return user;
    }

    @Test
    @DisplayName("Notification page order is deterministic when all rows share a timestamp")
    void notificationOrderDeterministicWhenTimestampsTie() {
        for (int i = 0; i < 6; i++) {
            notificationRepository.save(Notification.builder()
                    .recipient(recipient)
                    .actor(null)
                    .type("POST_LIKE")
                    .targetType("POST")
                    .targetId((long) (i + 1))
                    .contentPreview("tied " + i)
                    .isRead(false)
                    .build());
        }
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE notifications SET created_at = :t WHERE recipient_id = :r")
                .setParameter("t", TIED_TIMESTAMP)
                .setParameter("r", recipient.getId())
                .executeUpdate();
        entityManager.clear();

        List<Long> first = notificationService.getNotifications(
                recipient.getId(), "ALL", PageRequest.of(0, 20))
                .getContent().stream().map(NotificationResponse::getId).collect(Collectors.toList());
        List<Long> second = notificationService.getNotifications(
                recipient.getId(), "ALL", PageRequest.of(0, 20))
                .getContent().stream().map(NotificationResponse::getId).collect(Collectors.toList());

        System.out.printf("[pagination-stability] tied notification order: %s%n", first);
        assertThat(first).containsExactlyElementsOf(first.stream().sorted(java.util.Comparator.reverseOrder()).toList());
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("Message page order is deterministic when all rows share a timestamp")
    void messageOrderDeterministicWhenTimestampsTie() {
        User member = newUser("pagemember");
        Conversation conversation = conversationRepository.save(Conversation.builder()
                .type(ConversationType.DIRECT)
                .members(new java.util.ArrayList<>())
                .build());
        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(conversation).user(recipient).build());
        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(conversation).user(member).build());
        for (int i = 1; i <= 4; i++) {
            messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .sender(recipient)
                    .content("tied message " + i)
                    .build());
        }
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE messages SET created_at = :t WHERE conversation_id = :c")
                .setParameter("t", TIED_TIMESTAMP)
                .setParameter("c", conversation.getId())
                .executeUpdate();
        entityManager.clear();

        List<Long> first = conversationService.getMessages(conversation.getId(), recipient.getId(), PageRequest.of(0, 10))
                .getContent().stream().map(MessageResponse::getId).collect(Collectors.toList());
        List<Long> second = conversationService.getMessages(conversation.getId(), recipient.getId(), PageRequest.of(0, 10))
                .getContent().stream().map(MessageResponse::getId).collect(Collectors.toList());

        System.out.printf("[pagination-stability] tied message page order: %s%n", first);
        // the page is newest-first; with a full tie that means id DESC
        assertThat(first).containsExactlyElementsOf(first.stream().sorted(java.util.Comparator.reverseOrder()).toList());
        assertThat(second).isEqualTo(first);
    }
}
