package com.aloute.controller.api;

import com.aloute.common.api.ApiResponse;
import com.aloute.dto.request.DisableTwoFactorRequest;
import com.aloute.dto.request.EnableTwoFactorRequest;
import com.aloute.dto.request.RegenerateBackupCodesRequest;
import com.aloute.dto.response.TwoFactorEnableResponse;
import com.aloute.dto.response.TwoFactorSetupResponse;
import com.aloute.dto.response.TwoFactorStatusResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.TwoFactorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me/2fa")
@RequiredArgsConstructor
public class User2FaController {

    private final TwoFactorService twoFactorService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<TwoFactorStatusResponse>> getStatus() {
        Long userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(ApiResponse.success(twoFactorService.getStatus(userId)));
    }

    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<TwoFactorSetupResponse>> setup() {
        Long userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(ApiResponse.success(twoFactorService.setupTwoFactor(userId)));
    }

    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<TwoFactorEnableResponse>> enable(@Valid @RequestBody EnableTwoFactorRequest request) {
        Long userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(ApiResponse.success(twoFactorService.enableTwoFactor(userId, request.code())));
    }

    @PostMapping("/email/send-code")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendEmailSetupCode() {
        Long userId = currentUserProvider.requireUserId();
        twoFactorService.sendEmailSetupOtp(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Mã xác thực 6 số đã được gửi tới email của bạn.")));
    }

    @PostMapping("/email/enable")
    public ResponseEntity<ApiResponse<TwoFactorEnableResponse>> enableEmail(@Valid @RequestBody EnableTwoFactorRequest request) {
        Long userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(ApiResponse.success(twoFactorService.enableTwoFactorEmail(userId, request.code())));
    }

    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Map<String, Object>>> disable(@Valid @RequestBody DisableTwoFactorRequest request) {
        Long userId = currentUserProvider.requireUserId();
        twoFactorService.disableTwoFactor(userId, request.currentPassword(), request.code());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Đã tắt tính năng bảo mật 2 lớp thành công.")));
    }

    @PostMapping("/backup-codes")
    public ResponseEntity<ApiResponse<List<String>>> regenerateBackupCodes(@Valid @RequestBody RegenerateBackupCodesRequest request) {
        Long userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(ApiResponse.success(twoFactorService.regenerateBackupCodes(userId, request.currentPassword())));
    }
}
