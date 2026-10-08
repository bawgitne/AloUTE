package com.aloute.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseConfig {
    public FirebaseConfig(FirebaseProperties properties) throws IOException {
        if (!properties.isEnabled() || FirebaseApp.getApps().stream().anyMatch(app -> app.getName().equals(FirebaseApp.DEFAULT_APP_NAME))) {
            return;
        }
        try (FileInputStream credentials = new FileInputStream(properties.getCredentialsPath())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .setStorageBucket(properties.getBucket())
                    .build();
            FirebaseApp.initializeApp(options);
        }
    }
}
