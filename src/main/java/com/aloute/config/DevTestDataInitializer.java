package com.aloute.config;

import com.aloute.entity.Profile;
import com.aloute.entity.Topic;
import com.aloute.entity.User;
import com.aloute.repository.ProfileRepository;
import com.aloute.repository.TopicRepository;
import com.aloute.repository.UserRepository;
import com.aloute.security.authorization.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@org.springframework.context.annotation.Profile("dev")
public class DevTestDataInitializer {

    public static final String ADMIN_EMAIL = "admin@aloute.local";
    public static final String ADMIN_PASSWORD = "Admin@123";
    public static final String USER_EMAIL = "user@aloute.local";
    public static final String USER_PASSWORD = "User@123";

    /** Demo students for tools/demo-data.mjs; pre-verified so no OTP step is ever needed. */
    public static final String DEMO_STUDENT_PASSWORD = "Test@12345";

    private static final String[][] DEFAULT_TOPICS = {
            {"Học tập", "hoc-tap"},
            {"Nghiên cứu", "nghien-cuu"},
            {"UTE Connect", "ute-connect"},
            {"CLB Khoa", "clb-khoa"},
            {"Đồ án tốt nghiệp", "do-an-tot-nghiep"}
    };

    @Bean
    @Order(1)
    CommandLineRunner initDevTestAccounts(UserRepository userRepository,
                                          ProfileRepository profileRepository,
                                          TopicRepository topicRepository,
                                          PasswordEncoder passwordEncoder,
                                          PlatformTransactionManager transactionManager,
                                          org.springframework.core.env.Environment environment) {
        return args -> {
            // Defense in depth alongside ProductionProfileGuard: never seed
            // well-known passwords into an instance flagged as production.
            if (environment.getProperty(ProductionProfileGuard.PRODUCTION_MODE_PROPERTY, Boolean.class, false)) {
                return;
            }
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(status -> {
                createIfMissing(userRepository, profileRepository, passwordEncoder,
                        "admin", ADMIN_EMAIL, ADMIN_PASSWORD, "AloUTE Admin", Role.SYSTEM_ADMIN);
                createIfMissing(userRepository, profileRepository, passwordEncoder,
                        "user", USER_EMAIL, USER_PASSWORD, "AloUTE User", Role.USER);
                createIfMissing(userRepository, profileRepository, passwordEncoder,
                        "demoan", "demoan@aloute.test", DEMO_STUDENT_PASSWORD, "An Demo", Role.USER);
                createIfMissing(userRepository, profileRepository, passwordEncoder,
                        "demobinh", "demobinh@aloute.test", DEMO_STUDENT_PASSWORD, "Bình Demo", Role.USER);
                for (String[] t : DEFAULT_TOPICS) {
                    if (!topicRepository.existsBySlug(t[1])) {
                        Topic topic = new Topic();
                        topic.setName(t[0]);
                        topic.setSlug(t[1]);
                        topicRepository.save(topic);
                    }
                }
            });
        };
    }

    private void createIfMissing(UserRepository userRepository,
                                 ProfileRepository profileRepository,
                                 PasswordEncoder passwordEncoder,
                                 String username,
                                 String email,
                                 String rawPassword,
                                 String displayName,
                                 Role role) {
        if (userRepository.existsByEmailIgnoreCase(email) || userRepository.existsByUsernameIgnoreCase(username)) {
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setVerified(true);
        user.setPrivateAccount(false);
        user.setActive(true);
        user.setRole(role);
        user = userRepository.save(user);

        Profile profile = new Profile();
        profile.setUser(user);
        profile.setDisplayName(displayName);
        profileRepository.save(profile);
    }
}
