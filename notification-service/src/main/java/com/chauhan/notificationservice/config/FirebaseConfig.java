package com.chauhan.notificationservice.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;

/**
 * Configuration for Firebase Admin SDK initialization.
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${app.notification.firebase.credentials-path:classpath:firebase-service-account.json}")
    private String credentialsPath;

    @Bean
    public FirebaseApp firebaseApp(ResourceLoader resourceLoader) {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        try {
            Resource resource = resourceLoader.getResource(credentialsPath);
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(inputStream))
                            .build();
                    log.info("Initializing FirebaseApp from credentials path: {}", credentialsPath);
                    return FirebaseApp.initializeApp(options);
                }
            } else {
                log.warn("Firebase credentials file [{}] not found. Push Notification Channel will operate in mock mode.", credentialsPath);
            }
        } catch (Exception e) {
            log.error("Failed to initialize FirebaseApp from credentials path [{}]", credentialsPath, e);
        }
        return null;
    }
}
