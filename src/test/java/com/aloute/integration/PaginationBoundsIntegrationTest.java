package com.aloute.integration;

import com.aloute.entity.User;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import com.aloute.security.principal.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-16: pageable endpoints must not serve unbounded pages. spring.data.web
 * .pageable.max-page-size=100 clamps oversized ?size requests on every
 * Pageable-resolved endpoint (feeds, conversations, notifications, lists).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class PaginationBoundsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    private Authentication auth;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        User u = new User();
        u.setUsername("pagebound_" + suffix);
        u.setEmail("pagebound_" + suffix + "@aloute.test");
        u.setPasswordHash("$2a$10$placeholder");
        u.setVerified(true);
        u.setActive(true);
        u.setRole(Role.USER);
        u = userRepository.saveAndFlush(u);
        UserPrincipal principal = UserPrincipal.from(u);
        auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("BL-16: feed page size is clamped to 100")
    void feedPageSizeClamped() throws Exception {
        mockMvc.perform(get("/api/feed/explore?size=1000").with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.size").value(100));
    }

    @Test
    @DisplayName("BL-16: home feed oversized request is clamped, not served unbounded")
    void homeFeedPageSizeClamped() throws Exception {
        mockMvc.perform(get("/api/feed?size=500").with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.size").value(100));
    }

    @Test
    @DisplayName("Normal page size is preserved")
    void normalSizePreserved() throws Exception {
        mockMvc.perform(get("/api/feed/explore?size=25").with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.size").value(25));
    }
}
