package com.aloute.integration;

import com.aloute.entity.Profile;
import com.aloute.entity.User;
import com.aloute.repository.PostRepository;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.jwt.JwtTokenProvider;
import com.aloute.security.principal.UserPrincipal;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALOUTE_JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ALOUTE_JWT_EXPIRATION_SECONDS=3600"
})
class UsernameLoginAndPostCreationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PlatformTransactionManager transactionManager;

    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        // Commit user+profile immediately (same pattern as DevTestDataInitializer) so the JWT
        // authentication filter sees durable rows instead of the test-pending transaction.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        testUser = tx.execute(status -> userRepository.findByUsernameIgnoreCase("test_username_login")
                .orElseGet(() -> {
                    User u = new User();
                    u.setUsername("test_username_login");
                    u.setEmail("test_username_login@aloute.edu.vn");
                    u.setPasswordHash(passwordEncoder.encode("Password123!"));
                    u.setVerified(true);
                    u.setVerificationRequired(false);
                    u.setActive(true);
                    u = userRepository.save(u);
                    Profile profile = new Profile();
                    profile.setUser(u);
                    profile.setDisplayName("Test Username Login");
                    profileRepository.save(profile);
                    return u;
                }));

        jwtToken = jwtTokenProvider.generate(UserPrincipal.from(testUser));
    }

    @Test
    @DisplayName("User can authenticate using their plain username instead of email")
    void loginWithUsernameSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test_username_login",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("test_username_login"))
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    @DisplayName("User can authenticate using their email address")
    void loginWithEmailSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test_username_login@aloute.edu.vn",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("test_username_login"));
    }

    @Test
    @DisplayName("Creating post without visibility defaults to PUBLIC")
    void createPostWithoutVisibility() throws Exception {
        var result = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Test post without visibility attribute"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.content").value("Test post without visibility attribute"))
                .andReturn();

        Number postIdNum = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        Long postId = postIdNum.longValue();

        // Now reply to this post without visibility field (mobile app comment format)
        mockMvc.perform(post("/api/posts/{postId}/replies", postId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "This is a comment reply from mobile app"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("This is a comment reply from mobile app"))
                .andExpect(jsonPath("$.data.parentPostId").value(postId));

        // Get replies list
        mockMvc.perform(get("/api/posts/{postId}/replies", postId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].content").value("This is a comment reply from mobile app"));
    }
}
