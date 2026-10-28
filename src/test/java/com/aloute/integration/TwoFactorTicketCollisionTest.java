package com.aloute.integration;

import com.aloute.entity.User;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.crypto.TwoFactorEncryptionService;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.UserPrincipal;
import com.aloute.service.TotpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REL-2FA-02 (release-candidate E2E): two valid logins inside the same second used to mint the
 * same 2FA ticket JWT (iat/exp only have second resolution and there was no jti), so the
 * one-time ticket store rejected the second legitimate challenge with
 * AUTH_2FA_TICKET_ALREADY_USED. Replaying one captured ticket must still be refused.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class TwoFactorTicketCollisionTest {

    private static final String PASSWORD = "Correct@Password1";
    private static final String TOTP_SECRET = "JBSWY3DPEHPK3PXP";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TwoFactorEncryptionService twoFactorEncryptionService;
    @Autowired private TotpService totpService;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ObjectMapper objectMapper;

    private String email;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        email = "ticketcol_" + suffix + "@aloute.test";
        User u = new User();
        u.setUsername("ticketcol_" + suffix);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setVerified(true);
        u.setActive(true);
        u.setRole(Role.USER);
        u.setTwoFactorEnabled(true);
        u.setTwoFactorSecret(twoFactorEncryptionService.encrypt(TOTP_SECRET));
        u.setTwoFactorConfirmedAt(Instant.now());
        u.setTwoFactorType("TOTP");
        userRepository.saveAndFlush(u);
    }

    /** Mirrors the repro: two logins back to back, well inside the same second. */
    private String[] twoLoginTickets() throws Exception {
        String first = loginTicket();
        String second = loginTicket();
        return new String[]{first, second};
    }

    private String loginTicket() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String ticket = body.at("/data/twoFactorTicket").asText("");
        assertThat(ticket).as("a 2FA-enabled login must return a ticket").isNotBlank();
        return ticket;
    }

    private void verifyTicket(String ticket, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/auth/2fa/verify-login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"twoFactorTicket\":\"" + ticket
                                + "\",\"code\":\"" + totpService.generateCurrentCode(TOTP_SECRET)
                                + "\",\"isBackupCode\":false}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    @DisplayName("REL-2FA-02: two tickets minted in the same second are different JWTs")
    void ticketsMintedInTheSameSecondDiffer() {
        String a = jwtTokenProvider.generateTwoFactorTicket(userRepository.findByEmailIgnoreCase(email).orElseThrow().getId(), "TOTP", 300, 0L);
        String b = jwtTokenProvider.generateTwoFactorTicket(userRepository.findByEmailIgnoreCase(email).orElseThrow().getId(), "TOTP", 300, 0L);
        assertThat(a).isNotEqualTo(b);
        // Both still carry the challenge semantics the login flow depends on.
        assertThat(jwtTokenProvider.parseTwoFactorTicket(a).getSubject())
                .isEqualTo(jwtTokenProvider.parseTwoFactorTicket(b).getSubject());
    }

    @Test
    @DisplayName("REL-2FA-02: two back-to-back logins both verify (second is not a false replay)")
    void secondConcurrentLoginIsNotRejectedAsReplay() throws Exception {
        String[] tickets = twoLoginTickets();

        verifyTicket(tickets[0], 200);
        verifyTicket(tickets[1], 200);
    }

    @Test
    @DisplayName("REL-2FA-02: replaying one consumed ticket is still refused")
    void replayOfConsumedTicketStillRefused() throws Exception {
        String[] tickets = twoLoginTickets();

        verifyTicket(tickets[0], 200);
        // Re-presenting the SAME (already consumed) ticket must stay blocked.
        verifyTicket(tickets[0], 401);
        // The second, independently minted ticket remains usable.
        verifyTicket(tickets[1], 200);
    }

    @Test
    @DisplayName("A session token still carries its own jti (no regression)")
    void sessionTokenJtiUnchanged() {
        User u = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        String session = jwtTokenProvider.generate(UserPrincipal.from(u));
        assertThat(session).isNotEqualTo(
                jwtTokenProvider.generate(UserPrincipal.from(u)));
    }
}
