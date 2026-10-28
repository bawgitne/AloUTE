package com.aloute.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
@Slf4j
public class FirebaseConfig {

    public FirebaseConfig(FirebaseProperties properties) {
        if (FirebaseApp.getApps().stream().anyMatch(app -> app.getName().equals(FirebaseApp.DEFAULT_APP_NAME))) {
            return;
        }

        String configuredPath = properties.getCredentialsPath();
        InputStream serviceAccount = null;

        try {
            if (configuredPath != null && !configuredPath.isBlank()) {
                File file = new File(configuredPath);
                if (file.exists() && file.length() > 10) {
                    serviceAccount = new FileInputStream(file);
                } else {
                    ClassPathResource resource = new ClassPathResource(configuredPath);
                    if (resource.exists() && resource.contentLength() > 10) {
                        serviceAccount = resource.getInputStream();
                    }
                }
            }

            // Fallback: check standard locations for serviceAccountKey.json
            if (serviceAccount == null) {
                File rootFile = new File("serviceAccountKey.json");
                if (rootFile.exists() && rootFile.length() > 10) {
                    serviceAccount = new FileInputStream(rootFile);
                } else {
                    ClassPathResource classpathFile = new ClassPathResource("serviceAccountKey.json");
                    if (classpathFile.exists() && classpathFile.contentLength() > 10) {
                        serviceAccount = classpathFile.getInputStream();
                    }
                }
            }

            if (serviceAccount != null) {
                FirebaseOptions.Builder builder = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount));

                if (properties.getBucket() != null && !properties.getBucket().isBlank()) {
                    builder.setStorageBucket(properties.getBucket());
                }

                FirebaseApp.initializeApp(builder.build());
                log.info("FirebaseApp initialized successfully with service account.");
            } else if (properties.isEnabled()) {
                try {
                    FirebaseOptions.Builder builder = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.getApplicationDefault());
                    if (properties.getBucket() != null && !properties.getBucket().isBlank()) {
                        builder.setStorageBucket(properties.getBucket());
                    }
                    FirebaseApp.initializeApp(builder.build());
                    log.info("FirebaseApp initialized with application default credentials.");
                } catch (Exception adcEx) {
                    log.warn("Firebase is enabled but no credentials were available: {}", adcEx.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to initialize FirebaseApp: {}", ex.getMessage(), ex);
        } finally {
            if (serviceAccount != null) {
                try {
                    serviceAccount.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
