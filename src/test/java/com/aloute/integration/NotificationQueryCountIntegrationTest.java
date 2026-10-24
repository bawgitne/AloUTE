package com.aloute.integration;

import com.aloute.dto.response.NotificationResponse;
import com.aloute.entity.Notification;
import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.NotificationRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.service.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query-count regression tests for the notification list assembly
 * (NotificationServiceImpl#getNotifications).
 *
 * A realistic mailbox is built in-test: 30 notifications for one recipient,
 * produced by 8 distinct actors across LIKE/REPLY/FOLLOW/MENTION types, with a
 * second recipient for isolation checks. The persistence context is cleared
 * before counting so the service call pays the same lazy loads a real request
 * would. Actual statement counts are printed for the evidence log.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class NotificationQueryCountIntegrationTest {

    private static final int ACTOR_COUNT = 8;
    private static final int NOTIFICATION_COUNT = 30;
    private static final String[] TYPES = {"POST_LIKE", "POST_REPLY", "FOLLOW", "POST_MENTION", "REPLY_LIKE"};

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private EntityManager entityManager;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    private User recipient;
    private User otherRecipient;

    @BeforeEach
    void seedRealisticMailbox() {
        recipient = newUser("notifcount_recipient");
        otherRecipient = newUser("notifcount_other");

        List<User> actors = new ArrayList<>();
        for (int i = 0; i < ACTOR_COUNT; i++) {
            actors.add(newUser("notifcount_actor_" + i));
        }

        for (int i = 0; i < NOTIFICATION_COUNT; i++) {
            Notification n = Notification.builder()
                    .recipient(recipient)
                    .actor(actors.get(i % ACTOR_COUNT))
                    .type(TYPES[i % TYPES.length])
                    .targetType("POST")
                    .targetId((long) (i + 1))
                    .contentPreview("notification body " + i)
                    .isRead(i % 3 == 0)
                    .build();
            notificationRepository.save(n);
        }
        notificationRepository.save(Notification.builder()
                .recipient(otherRecipient)
                .actor(actors.get(0))
                .type("POST_LIKE")
                .targetType("POST")
                .targetId(999L)
                .contentPreview("other mailbox")
                .isRead(false)
                .build());

        // The service must resolve actors and profiles from the database, not
        // from this test's persistence context.
        entityManager.flush();
        entityManager.clear();
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

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private long countStatements(Runnable serviceCall) {
        // Each measurement must pay the same lazy loads a real request would,
        // so the persistence context is cleared first.
        entityManager.clear();
        Statistics stats = statistics();
        stats.clear();
        serviceCall.run();
        return stats.getPrepareStatementCount();
    }

    @Test
    @DisplayName("Notification list stays batched: one profile query per page, not per notification")
    void notificationListStaysBatched() {
        var page0 = new AtomicReference<Page<NotificationResponse>>(null);
        long queriesPage0 = countStatements(() -> page0.set(notificationService.getNotifications(
                recipient.getId(), "ALL", PageRequest.of(0, 20))));
        System.out.printf("[notif-query-count] list ALL page0 (20 rows): %d statements%n", queriesPage0);
        // count + content + one batched profile lookup; the pre-batching
        // implementation paid 2 statements per row (~42 for this page).
        assertThat(queriesPage0).as("JDBC statements for a 20-row notification page").isLessThanOrEqualTo(5);

        var page1 = new AtomicReference<Page<NotificationResponse>>(null);
        long queriesPage1 = countStatements(() -> page1.set(notificationService.getNotifications(
                recipient.getId(), "ALL", PageRequest.of(1, 20))));
        System.out.printf("[notif-query-count] list ALL page1 (10 rows): %d statements%n", queriesPage1);
        assertThat(queriesPage1).as("JDBC statements for the second page").isLessThanOrEqualTo(5);

        // Growth must be bounded by the page size, not by the mailbox size:
        // a 5-row page costs the same as a 20-row page (±1 for noise).
        var smallPage = new AtomicReference<Page<NotificationResponse>>(null);
        long queriesSmall = countStatements(() -> smallPage.set(notificationService.getNotifications(
                recipient.getId(), "ALL", PageRequest.of(0, 5))));
        System.out.printf("[notif-query-count] list ALL size5: %d statements%n", queriesSmall);
        assertThat(queriesPage0 - queriesSmall).as("query growth from 5 to 20 rows").isLessThanOrEqualTo(1);

        // Payload correctness: actor metadata comes from profiles, not usernames alone.
        List<NotificationResponse> content = page0.get().getContent();
        assertThat(content).hasSize(20);
        assertThat(content).allSatisfy(n -> {
            assertThat(n.getRecipientId()).isEqualTo(recipient.getId());
            assertThat(n.getActor()).isNotNull();
            assertThat(n.getActor().username()).startsWith("notifcount_actor_");
            assertThat(n.getActor().displayName())
                    .isEqualTo(n.getActor().username() + " display");
        });
        // newest first (ties resolved by id DESC); seeded ids increase with i
        List<Long> ids = content.stream().map(NotificationResponse::getId).toList();
        assertThat(ids).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        // read status preserved: page 0 covers the 20 newest rows (i = 10..29),
        // of which i % 3 == 0 marks exactly 6 as read
        assertThat(content.stream().filter(NotificationResponse::isRead).count()).isEqualTo(6);
    }

    @Test
    @DisplayName("Filtered notification list keeps type filtering without extra per-row queries")
    void filteredListStaysBatched() {
        var page = new AtomicReference<Page<NotificationResponse>>(null);
        long queries = countStatements(() -> page.set(notificationService.getNotifications(
                recipient.getId(), "LIKE", PageRequest.of(0, 20))));
        System.out.printf("[notif-query-count] list LIKE: %d statements%n", queries);
        assertThat(queries).as("JDBC statements for a filtered notification page").isLessThanOrEqualTo(5);

        List<NotificationResponse> content = page.get().getContent();
        // seeded types cycle POST_LIKE, POST_REPLY, FOLLOW, POST_MENTION, REPLY_LIKE → 12 LIKE rows
        assertThat(content).hasSize(12);
        assertThat(content).allSatisfy(n ->
                assertThat(n.getType()).isIn("POST_LIKE", "REPLY_LIKE"));
    }

    @Test
    @DisplayName("Unread count stays a single statement regardless of mailbox size")
    void unreadCountStaysConstant() {
        var count = new AtomicReference<Long>(0L);
        long queries = countStatements(() -> count.set(notificationService.getUnreadCount(recipient.getId()).getUnreadCount()));
        System.out.printf("[notif-query-count] unread-count: %d statements%n", queries);
        assertThat(queries).as("JDBC statements for unread-count").isLessThanOrEqualTo(2);
        assertThat(count.get()).isEqualTo(20L);
    }

    @Test
    @DisplayName("Recipient isolation: another recipient sees only their own notifications")
    void recipientIsolationPreserved() {
        var page = new AtomicReference<Page<NotificationResponse>>(null);
        long queries = countStatements(() -> page.set(notificationService.getNotifications(
                otherRecipient.getId(), "ALL", PageRequest.of(0, 20))));
        System.out.printf("[notif-query-count] list for other recipient: %d statements%n", queries);
        assertThat(queries).isLessThanOrEqualTo(5);

        assertThat(page.get().getContent()).hasSize(1);
        assertThat(page.get().getContent().get(0).getRecipientId()).isEqualTo(otherRecipient.getId());
        assertThat(page.get().getContent().get(0).getContentPreview()).isEqualTo("other mailbox");
    }
}
