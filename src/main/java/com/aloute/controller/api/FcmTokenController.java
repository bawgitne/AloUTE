package com.aloute.controller.api;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.request.RegisterFcmTokenRequest;
import com.aloute.entity.UserFcmToken;
import com.aloute.repository.UserFcmTokenRepository;
import com.aloute.security.currentuser.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/notifications/fcm-token")
@RequiredArgsConstructor
@Slf4j
public class FcmTokenController {

    private final CurrentUserProvider currentUserProvider;
    private final UserFcmTokenRepository fcmTokenRepository;

    @PostMapping
    @Transactional
    public ApiResponse<Void> registerToken(@Valid @RequestBody RegisterFcmTokenRequest request) {
        Long userId = currentUserProvider.requireUserId();
        String token = request.getToken().trim();

        UserFcmToken fcmToken = fcmTokenRepository.findByToken(token)
                .orElseGet(() -> UserFcmToken.builder()
                        .userId(userId)
                        .token(token)
                        .build());

        fcmToken.setUserId(userId);
        fcmToken.setUpdatedAt(Instant.now());
        fcmTokenRepository.save(fcmToken);

        log.info("Registered FCM token for userId: {}", userId);
        return ApiResponse.success(null);
    }

    @DeleteMapping
    @Transactional
    public ApiResponse<Void> unregisterToken(@Valid @RequestBody RegisterFcmTokenRequest request) {
        fcmTokenRepository.deleteByToken(request.getToken().trim());
        return ApiResponse.success(null);
    }
}
