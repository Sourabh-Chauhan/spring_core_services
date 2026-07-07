package com.chauhan.authservice.security;

import com.chauhan.authservice.config.AppConstants;
import com.chauhan.authservice.entity.Provider;
import com.chauhan.authservice.entity.RefreshToken;
import com.chauhan.authservice.entity.Role;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.repository.RoleRepository;
import com.chauhan.authservice.repository.UserRepository;
import com.chauhan.authservice.service.impl.RefreshTokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.chauhan.authservice.event.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2SuccessHandler.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtilService cookieUtilService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String clientRegistrationId = oauthToken.getAuthorizedClientRegistrationId();
        
        logger.info("OAuth2 success callback for provider: {}", clientRegistrationId);

        String email = null;
        String name = null;
        String imageUrl = null;

        if ("google".equalsIgnoreCase(clientRegistrationId)) {
            email = oAuth2User.getAttribute("email");
            name = oAuth2User.getAttribute("name");
            imageUrl = oAuth2User.getAttribute("picture");
        } else if ("github".equalsIgnoreCase(clientRegistrationId)) {
            email = oAuth2User.getAttribute("email");
            name = oAuth2User.getAttribute("name");
            if (name == null) {
                name = oAuth2User.getAttribute("login");
            }
            imageUrl = oAuth2User.getAttribute("avatar_url");

            // GitHub email can be null if user has private email
            if (email == null) {
                email = oAuth2User.getAttribute("login") + "@github.com";
            }
        }

        if (email == null) {
            logger.error("Could not retrieve email from OAuth2 provider");
            response.sendRedirect("http://localhost:3000/login?error=email_not_found");
            return;
        }

        Provider provider = Provider.LOCAL;
        if ("google".equalsIgnoreCase(clientRegistrationId)) {
            provider = Provider.GOOGLE;
        } else if ("github".equalsIgnoreCase(clientRegistrationId)) {
            provider = Provider.GITHUB;
        }

        String providerId = oAuth2User.getName();

        // 1. Find or create the user account
        Optional<User> userOpt = userRepository.findByEmail(email);
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            // Link account if registering from social first time
            if (user.getProvider() == Provider.LOCAL) {
                user.setProvider(provider);
                user.setProviderId(providerId);
                user.setEmailVerified(true); // OAuth2 verified
                user = userRepository.save(user);
                logger.info("Linked OAuth2 provider {} to existing local user with email {}", provider, email);
            }
        } else {
            // Provision new user
            Role defaultRole = roleRepository.findByName("ROLE_" + AppConstants.USER_ROLE)
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .id(UUID.randomUUID())
                            .name("ROLE_" + AppConstants.USER_ROLE)
                            .build()));

            user = User.builder()
                    .email(email)
                    .name(name)
                    .image(imageUrl)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .provider(provider)
                    .providerId(providerId)
                    .enable(true)
                    .emailVerified(true)
                    .roles(new HashSet<>(List.of(defaultRole)))
                    .build();

            user = userRepository.save(user);
            logger.info("Provisioned new OAuth2 user with email {}", email);
        }

        // 2. Generate Tokens
        String userAgent = request.getHeader("User-Agent");
        String ipAddress = request.getRemoteAddr();
        RefreshToken refreshTokenEntity = refreshTokenService.createRefreshToken(user, ipAddress, userAgent);
        String accessToken = jwtUtil.generateAccessToken(user, refreshTokenEntity.getJti());
        String refreshTokenString = jwtUtil.generateRefreshToken(user, refreshTokenEntity.getJti());

        // 3. Attach Refresh Token Cookie
        cookieUtilService.attachRefreshCookie(response, refreshTokenString, (int) jwtUtil.getRefreshTtlSeconds());
        cookieUtilService.addNoStoreHeaders(response);

        // Publish audit event for successful OAuth2 login
        eventPublisher.publishEvent(new AuditEvent(this, AppConstants.AUDIT_EVENT_LOGIN_SUCCESS, user.getEmail(), ipAddress, userAgent, "OAuth2 login successful via provider: " + clientRegistrationId));

        // 4. Redirect user to frontend app
        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/oauth2/redirect")
                .queryParam("token", accessToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
