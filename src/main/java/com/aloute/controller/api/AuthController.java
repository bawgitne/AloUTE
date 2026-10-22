package com.aloute.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aloute.common.api.ApiResponse;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.request.LoginRequest;
import com.aloute.dto.request.RegisterRequest;
import com.aloute.dto.request.ForgotPasswordRequest;
import com.aloute.dto.request.ResetPasswordRequest;
import com.aloute.dto.request.VerifyRegistrationOtpRequest;
import com.aloute.dto.request.ResendRegistrationOtpRequest;
import com.aloute.dto.response.UserResponse;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.security.jwt.JwtAuthenticationFilter;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.service.AuthService;
import com.aloute.service.PasswordResetService;
import com.aloute.service.RegistrationOtpService;
import com.aloute.dto.request.VerifyTwoFactorLoginRequest;
import com.aloute.dto.request.SendEmailFallbackOtpRequest;
import com.aloute.dto.response.LoginResponse;
import com.aloute.service.TwoFactorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final AuthService authService;
    private final TwoFactorService twoFactorService;
    private final JwtTokenProvider tokenProvider;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordResetService passwordResetService;
    private final RegistrationOtpService registrationOtpService;

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.initiateForgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/verify-registration-otp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyRegistrationOtp(@Valid @RequestBody VerifyRegistrationOtpRequest request) {
        registrationOtpService.verifyOtp(request.email(), request.otp());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Tài khoản đã được xác thực thành công.")));
    }

    @PostMapping("/resend-registration-otp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resendRegistrationOtp(@Valid @RequestBody ResendRegistrationOtpRequest request) {
        registrationOtpService.resendOtp(request.email());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Yêu cầu gửi lại mã xác thực đã được tiếp nhận. Vui lòng kiểm tra hộp thư nếu thông tin tài khoản hợp lệ.")));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthService.LoginOutcome outcome = authService.login(request);
        if (outcome.twoFactorRequired()) {
            String ticket = tokenProvider.generateTwoFactorTicket(outcome.userId(), outcome.twoFactorType(), 300); // 5 minutes
            if ("EMAIL".equalsIgnoreCase(outcome.twoFactorType())) {
                try {
                    twoFactorService.sendEmailFallbackOtp(ticket);
                } catch (ApiException ex) {
                    if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                        log.info("Email OTP already active within cooldown for user {}", outcome.userId());
                    } else {
                        throw ex;
                    }
                }
            }
            return ResponseEntity.ok(ApiResponse.success(LoginResponse.twoFactorRequired(ticket, outcome.twoFactorType(), outcome.maskedEmail())));
        }

        String token = tokenProvider.generate(outcome.principal());
        ResponseCookie cookie = authCookie(token, tokenProvider.expirationSeconds(), servletRequest.isSecure());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(LoginResponse.success(outcome.user(), token)));
    }

    @PostMapping("/2fa/verify-login")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyTwoFactorLogin(
            @Valid @RequestBody VerifyTwoFactorLoginRequest request,
            HttpServletRequest servletRequest) {
        TwoFactorService.AuthenticatedSession session = twoFactorService.verifyLogin(request);
        ResponseCookie cookie = authCookie(session.token(), tokenProvider.expirationSeconds(), servletRequest.isSecure());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(LoginResponse.success(session.user(), session.token())));
    }

    @PostMapping("/2fa/send-email-fallback")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendEmailFallbackOtp(
            @Valid @RequestBody SendEmailFallbackOtpRequest request) {
        twoFactorService.sendEmailFallbackOtp(request.twoFactorTicket());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Đã gửi mã xác thực dự phòng đến email sinh viên của bạn. Mã có hiệu lực trong 5 phút.")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Long userId = currentUserProvider.requireUserId();
        String token = JwtAuthenticationFilter.resolveToken(request);
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHENTICATED", "Authentication required");
        }

        authService.logout(token, userId);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookie("", 0, request.isSecure()).toString())
                .build();
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() { return ApiResponse.success(authService.getCurrentUser(currentUserProvider.requireUserId())); }

    private ResponseCookie authCookie(String value, long maxAgeSeconds, boolean secure) {
        return ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME,value).httpOnly(true).secure(secure)
                .sameSite("Lax").path("/").maxAge(Duration.ofSeconds(maxAgeSeconds)).build();
    }
}
