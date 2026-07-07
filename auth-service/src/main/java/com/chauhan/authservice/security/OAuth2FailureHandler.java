package com.chauhan.authservice.security;

import com.chauhan.authservice.config.AppConstants;
import com.chauhan.authservice.event.AuditEvent;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2FailureHandler.class);
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        
        logger.error("OAuth2 authentication failed: {}", exception.getMessage(), exception);

        String userAgent = request.getHeader("User-Agent");
        String ipAddress = request.getRemoteAddr();
        eventPublisher.publishEvent(new AuditEvent(this, AppConstants.AUDIT_EVENT_LOGIN_FAILURE, "oauth-failure", ipAddress, userAgent, "OAuth2 login failed: " + exception.getMessage()));

        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/login")
                .queryParam("error", exception.getLocalizedMessage())
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}

