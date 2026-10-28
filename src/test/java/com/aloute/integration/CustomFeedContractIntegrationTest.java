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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-14: /api/feeds/{feedId}/posts used to ignore feedId entirely and return
 * Explore-feed posts — wrong semantics. Custom feeds are contract-only (no
 * entity, no creation path), so any feedId answers 404 and the list endpoint
 * keeps returning the documented empty stub.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class CustomFeedContractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    private Authentication auth;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        User u = new User();
        u.setUsername("feedstub_" + suffix);
        u.setEmail("feedstub_" + suffix + "@aloute.test");
        u.setPasswordHash("$2a$10$placeholder");
        u.setVerified(true);
        u.setActive(true);
        u.setRole(Role.USER);
        u = userRepository.saveAndFlush(u);
        UserPrincipal principal = UserPrincipal.from(u);
        auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("BL-14: any feedId returns 404 FEED_NOT_FOUND, never foreign feed data")
    void feedPostsReturn404() throws Exception {
        mockMvc.perform(get("/api/feeds/999/posts?size=2").with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FEED_NOT_FOUND"));
    }

    @Test
    @DisplayName("Custom feed list remains the documented empty stub")
    void feedListRemainsEmptyStub() throws Exception {
        mockMvc.perform(get("/api/feeds").with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
