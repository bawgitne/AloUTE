package com.aloute.controller.api;

import com.aloute.dto.request.RegisterFcmTokenRequest;
import com.aloute.entity.UserFcmToken;
import com.aloute.repository.UserFcmTokenRepository;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.FcmTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FcmTokenControllerTest {

    private CurrentUserProvider currentUserProvider;
    private UserFcmTokenRepository fcmTokenRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        currentUserProvider = mock(CurrentUserProvider.class);
        fcmTokenRepository = mock(UserFcmTokenRepository.class);
        FcmTokenService fcmTokenService = new FcmTokenService(fcmTokenRepository);
        FcmTokenController controller = new FcmTokenController(currentUserProvider, fcmTokenService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.aloute.common.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("registerToken successfully saves FCM token for authenticated user")
    void registerTokenSuccess() throws Exception {
        when(currentUserProvider.requireUserId()).thenReturn(100L);
        when(fcmTokenRepository.findByToken("test-fcm-token")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/notifications/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"test-fcm-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(fcmTokenRepository).save(any(UserFcmToken.class));
    }

    @Test
    @DisplayName("unregisterToken removes a token owned by the current user")
    void unregisterTokenSuccess() throws Exception {
        when(currentUserProvider.requireUserId()).thenReturn(100L);
        UserFcmToken owned = UserFcmToken.builder().userId(100L).token("test-fcm-token").build();
        when(fcmTokenRepository.findByToken("test-fcm-token")).thenReturn(Optional.of(owned));

        mockMvc.perform(delete("/api/notifications/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"test-fcm-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(fcmTokenRepository).delete(owned);
    }

    @Test
    @DisplayName("unregisterToken returns 404 when the token belongs to another user or does not exist")
    void unregisterForeignOrUnknownTokenReturns404() throws Exception {
        when(currentUserProvider.requireUserId()).thenReturn(100L);
        UserFcmToken foreign = UserFcmToken.builder().userId(200L).token("test-fcm-token").build();
        when(fcmTokenRepository.findByToken("test-fcm-token")).thenReturn(Optional.of(foreign));

        mockMvc.perform(delete("/api/notifications/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"test-fcm-token\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        verify(fcmTokenRepository, never()).delete(any(UserFcmToken.class));

        when(fcmTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/notifications/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"unknown\"}"))
                .andExpect(status().isNotFound());
    }
}
