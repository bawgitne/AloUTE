package com.aloute.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aloute.firebase")
@Getter
@Setter
public class FirebaseProperties {
    private boolean enabled;
    private String bucket;
    private String credentialsPath;
}
