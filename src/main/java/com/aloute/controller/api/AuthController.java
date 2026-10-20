package com.aloute.controller.api;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
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
public class AuthController {
    private final AuthService authService;
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
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Mã xác thực mới đã được gửi tới email của bạn.")));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthService.AuthenticatedUser authenticated=authService.login(request);
        String token=tokenProvider.generate(authenticated.principal());
        ResponseCookie cookie=authCookie(token, tokenProvider.expirationSeconds(), servletRequest.isSecure());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie.toString()).body(ApiResponse.success(authenticated.user()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        currentUserProvider.requireUserId();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,authCookie("",0,request.isSecure()).toString()).build();
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() { return ApiResponse.success(authService.getCurrentUser(currentUserProvider.requireUserId())); }

    private ResponseCookie authCookie(String value, long maxAgeSeconds, boolean secure) {
        return ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME,value).httpOnly(true).secure(secure)
                .sameSite("Lax").path("/").maxAge(Duration.ofSeconds(maxAgeSeconds)).build();
    }
}
