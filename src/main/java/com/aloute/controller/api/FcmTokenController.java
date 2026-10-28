package com.aloute.controller.api;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.request.RegisterFcmTokenRequest;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.FcmTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications/fcm-token")
@RequiredArgsConstructor
@Slf4j
public class FcmTokenController {

    private final CurrentUserProvider currentUserProvider;
    private final FcmTokenService fcmTokenService;

    @PostMapping
    public ApiResponse<Void> registerToken(@Valid @RequestBody RegisterFcmTokenRequest request) {
        Long userId = currentUserProvider.requireUserId();
        fcmTokenService.registerToken(userId, request.getToken());
        return ApiResponse.success(null);
    }

    @DeleteMapping
    public ApiResponse<Void> unregisterToken(@Valid @RequestBody RegisterFcmTokenRequest request) {
        Long userId = currentUserProvider.requireUserId();
        boolean removed = fcmTokenService.unregisterToken(userId, request.getToken());
        if (!removed) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FCM_TOKEN_NOT_FOUND",
                    "FCM token not registered for the current user");
        }
        return ApiResponse.success(null);
    }
}
