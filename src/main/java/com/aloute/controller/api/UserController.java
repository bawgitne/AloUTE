package com.aloute.controller.api;

import lombok.RequiredArgsConstructor;
import com.aloute.common.api.ApiResponse;
import com.aloute.dto.request.UpdateProfileRequest;
import com.aloute.dto.response.PostResponse;
import com.aloute.dto.response.ProfileResponse;
import com.aloute.dto.response.UserSummary;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.PostService;
import com.aloute.service.SocialService;
import com.aloute.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.aloute.dto.request.ChangePasswordRequest;
import com.aloute.entity.User;
import com.aloute.security.jwt.JwtAuthenticationFilter;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;
    private final PostService postService;
    private final SocialService socialService;
    private final CurrentUserProvider currentUserProvider;
    private final JwtTokenProvider tokenProvider;

    @GetMapping("/{userId}")
    public ApiResponse<ProfileResponse> getProfile(@PathVariable @Positive Long userId) {
        return ApiResponse.success(userService.getProfile(userId));
    }

    @GetMapping("/by-username/{username}")
    public ApiResponse<ProfileResponse> byUsername(@PathVariable String username) {
        return ApiResponse.success(userService.getProfileByUsername(username));
    }

    @GetMapping({"/me/profile", "/profile"})
    public ApiResponse<ProfileResponse> getMyProfile() {
        Long currentUserId = currentUserProvider.requireUserId();
        return ApiResponse.success(userService.getProfile(currentUserId));
    }

    @RequestMapping(value = {"/me/profile", "/profile"}, method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ApiResponse<ProfileResponse> update(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userService.updateCurrentProfile(currentUserProvider.requireUserId(), request));
    }

    @PostMapping({"/me/password", "/password"})
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        Long currentUserId = currentUserProvider.requireUserId();
        User user = userService.changePassword(currentUserId, request);
        UserPrincipal principal = UserPrincipal.from(user);
        String token = tokenProvider.generate(principal);
        ResponseCookie cookie = authCookie(token, tokenProvider.expirationSeconds(), servletRequest.isSecure());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(Map.of("message", "Đổi mật khẩu thành công.")));
    }

    @PostMapping("/me/sessions/logout-others")
    public ResponseEntity<ApiResponse<Map<String, String>>> logoutOtherSessions(HttpServletRequest servletRequest) {
        Long currentUserId = currentUserProvider.requireUserId();
        User user = userService.logoutOtherSessions(currentUserId);
        UserPrincipal principal = UserPrincipal.from(user);
        String token = tokenProvider.generate(principal);
        ResponseCookie cookie = authCookie(token, tokenProvider.expirationSeconds(), servletRequest.isSecure());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(Map.of("message", "Đã đăng xuất khỏi tất cả các thiết bị khác thành công.")));
    }

    @PostMapping("/me/deactivate")
    public ResponseEntity<ApiResponse<Map<String, String>>> deactivateAccount(
            @Valid @RequestBody com.aloute.dto.request.DeactivateAccountRequest request,
            HttpServletRequest servletRequest
    ) {
        Long currentUserId = currentUserProvider.requireUserId();
        userService.deactivateAccount(currentUserId, request.password());
        ResponseCookie clearCookie = authCookie("", 0, servletRequest.isSecure());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(ApiResponse.success(Map.of("message", "Tài khoản của bạn đã được vô hiệu hóa tạm thời.")));
    }

    @GetMapping("/me/notifications/settings")
    public ApiResponse<com.aloute.dto.response.NotificationSettingsResponse> getNotificationSettings() {
        return ApiResponse.success(userService.getNotificationSettings(currentUserProvider.requireUserId()));
    }

    @PutMapping("/me/notifications/settings")
    public ApiResponse<com.aloute.dto.response.NotificationSettingsResponse> updateNotificationSettings(
            @Valid @RequestBody com.aloute.dto.request.UpdateNotificationSettingsRequest request) {
        return ApiResponse.success(userService.updateNotificationSettings(currentUserProvider.requireUserId(), request));
    }

    private ResponseCookie authCookie(String value, long maxAgeSeconds, boolean secure) {
        return ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, value).httpOnly(true).secure(secure)
                .sameSite("Lax").path("/").maxAge(Duration.ofSeconds(maxAgeSeconds)).build();
    }

    @GetMapping("/{userId}/posts")
    public ApiResponse<List<PostResponse>> posts(
            @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Long viewerId = currentUserProvider.getCurrentUserId().orElse(null);
        return postService.userPosts(userId, page, size, viewerId);
    }

    @GetMapping("/{userId}/followers")
    public ApiResponse<List<UserSummary>> followers(
            @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return socialService.followers(userId, PageRequest.of(page, size));
    }

    @GetMapping("/{userId}/following")
    public ApiResponse<List<UserSummary>> following(
            @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return socialService.following(userId, PageRequest.of(page, size));
    }
}
