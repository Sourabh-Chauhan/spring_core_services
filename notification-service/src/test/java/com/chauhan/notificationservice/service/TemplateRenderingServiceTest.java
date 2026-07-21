package com.chauhan.notificationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemplateRenderingServiceTest {

    private TemplateRenderingService templateRenderingService;

    @BeforeEach
    void setUp() {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode("HTML");
        templateResolver.setCharacterEncoding("UTF-8");
        templateEngine.setTemplateResolver(templateResolver);

        templateRenderingService = new TemplateRenderingService(templateEngine);
    }

    @Test
    void testRenderWelcomeEmailTemplate() {
        String renderedHtml = templateRenderingService.render("email/welcome-email", Map.of(
                "name", "Jane Doe",
                "verificationToken", "verify-token-xyz-123"
        ));

        assertNotNull(renderedHtml);
        assertTrue(renderedHtml.contains("Jane Doe"));
        assertTrue(renderedHtml.contains("verify-token-xyz-123"));
        assertTrue(renderedHtml.contains("Welcome to Spring Core Services"));
    }

    @Test
    void testRenderPasswordResetEmailTemplate() {
        String renderedHtml = templateRenderingService.render("email/password-reset-email", Map.of(
                "name", "John Smith",
                "resetToken", "reset-token-789"
        ));

        assertNotNull(renderedHtml);
        assertTrue(renderedHtml.contains("John Smith"));
        assertTrue(renderedHtml.contains("reset-token-789"));
        assertTrue(renderedHtml.contains("Password Reset Request"));
    }
}
