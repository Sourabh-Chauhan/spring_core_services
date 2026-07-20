package com.chauhan.notificationservice.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Twilio SDK initialization.
 */
@Configuration
@Slf4j
public class TwilioConfig {

    @Value("${app.notification.twilio.account-sid:AC_dummy_account_sid}")
    private String accountSid;

    @Value("${app.notification.twilio.auth-token:dummy_auth_token}")
    private String authToken;

    @PostConstruct
    public void initTwilio() {
        if (accountSid != null && accountSid.startsWith("AC") && !accountSid.contains("dummy")) {
            try {
                Twilio.init(accountSid, authToken);
                log.info("Twilio SDK successfully initialized with Account SID: {}", accountSid);
            } catch (Exception e) {
                log.error("Failed to initialize Twilio SDK", e);
            }
        } else {
            log.warn("Twilio Account SID is unconfigured or dummy [{}]. SMS channel will run in simulated mode.", accountSid);
            try {
                Twilio.init(accountSid, authToken);
            } catch (Exception e) {
                log.debug("Twilio dummy initialization skipped: {}", e.getMessage());
            }
        }
    }
}
