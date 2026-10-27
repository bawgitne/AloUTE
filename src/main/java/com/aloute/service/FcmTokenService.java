package com.aloute.service;

import com.aloute.entity.UserFcmToken;
import com.aloute.repository.UserFcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmTokenService {

    private final UserFcmTokenRepository fcmTokenRepository;

    @Transactional
    public void registerToken(Long userId, String token) {
        String normalized = token.trim();
        UserFcmToken fcmToken = fcmTokenRepository.findByToken(normalized)
                .orElseGet(() -> UserFcmToken.builder()
                        .userId(userId)
                        .token(normalized)
                        .build());

        // A browser push token belongs to the device/browser: the most recent
        // registrant on that device receives its notifications.
        fcmToken.setUserId(userId);
        fcmToken.setUpdatedAt(Instant.now());
        fcmTokenRepository.save(fcmToken);

        log.info("Registered FCM token for userId: {}", userId);
    }

    /**
     * Unregisters a token only when it belongs to the caller. Returns false for
     * unknown or foreign tokens so the controller can answer 404 without
     * disclosing whether the token exists for someone else.
     */
    @Transactional
    public boolean unregisterToken(Long userId, String token) {
        String normalized = token.trim();
        return fcmTokenRepository.findByToken(normalized)
                .filter(existing -> userId != null && userId.equals(existing.getUserId()))
                .map(existing -> {
                    fcmTokenRepository.delete(existing);
                    return true;
                })
                .orElse(false);
    }
}
