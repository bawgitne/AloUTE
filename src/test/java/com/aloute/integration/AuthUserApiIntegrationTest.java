package com.aloute.integration;

import com.aloute.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class AuthUserApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void clean() {
        // FK tables are recreated for the in-memory test context; tests use unique accounts.
    }

    @Test
    void registerLoginMePublicProfileFollowAndReportFlow() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"khoi_it","email":"khoi_it@example.com","password":"12345678"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"friend_it","email":"friend_it@example.com","password":"12345678"}
                                """))
                .andExpect(status().isCreated());

        Long currentId = userRepository.findByEmailIgnoreCase("khoi_it@example.com").orElseThrow().getId();
        Long friendId = userRepository.findByEmailIgnoreCase("friend_it@example.com").orElseThrow().getId();

        // Whitelist: public profile is available without authentication.
        mockMvc.perform(get("/api/users/{id}", friendId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("friend_it"));

        var loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"khoi_it@example.com","password":"12345678"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String token = loginResponse.getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith("aloute_access_token="))
                .findFirst()
                .map(c -> c.substring("aloute_access_token=".length(), c.indexOf(';')))
                .orElseThrow(() -> new IllegalStateException("Missing aloute_access_token cookie"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(currentId));

        mockMvc.perform(put("/api/social/follows/{id}", friendId)
                        .header("Authorization", "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportedUserId":%d,"reason":"SPAM","description":"integration test"}
                                """.formatted(friendId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void protectedEndpointRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
