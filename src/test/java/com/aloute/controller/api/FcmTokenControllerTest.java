package com.aloute.controller.api;

import com.aloute.dto.request.RegisterFcmTokenRequest;
import com.aloute.entity.UserFcmToken;
import com.aloute.repository.UserFcmTokenRepository;
import com.aloute.security.currentuser.CurrentUserProvider;
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
        FcmTokenController controller = new FcmTokenController(currentUserProvider, fcmTokenRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
    @DisplayName("unregisterToken successfully removes token")
    void unregisterTokenSuccess() throws Exception {
        mockMvc.perform(delete("/api/notifications/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"test-fcm-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(fcmTokenRepository).deleteByToken("test-fcm-token");
    }
}
