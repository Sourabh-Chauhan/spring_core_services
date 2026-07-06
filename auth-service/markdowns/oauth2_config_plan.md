# Implementation Plan: OAuth2 Configuration & Handlers

This plan details the steps required to configure Spring Security for OAuth2 login with social providers (Google, GitHub, etc.) and implement custom success and failure handlers to issue internal JWTs.

---

## 1. Add Dependencies

We will add the Spring Security OAuth2 Client starter to the dependencies list in `pom.xml`.

* **File to modify:** [pom.xml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/pom.xml)
* **Dependency to add:**
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-oauth2-client</artifactId>
  </dependency>
  ```

---

## 2. Configuration Properties

We will configure OAuth2 registration and provider properties for Google and GitHub in the YAML configuration. Placeholders with environment fallbacks will be used for client credentials.

* **File to modify:** [application-dev.yml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/resources/application-dev.yml)
* **Configurations to add:**
  ```yaml
  spring:
    security:
      oauth2:
        client:
          registration:
            google:
              client-id: ${GOOGLE_CLIENT_ID}
              client-secret: ${GOOGLE_CLIENT_SECRET}
              scope:
                - email
                - profile
            github:
              client-id: ${GITHUB_CLIENT_ID}
              client-secret: ${GITHUB_CLIENT_SECRET}
              scope:
                - read:user
                - user:email
  ```

---

## 3. Custom OAuth2 Handlers

To integrate OAuth2 authentication with our stateless JWT architecture, we will implement custom success and failure handlers.

### A. OAuth2 Success Handler (`OAuth2SuccessHandler`)

Upon successful authentication by the third-party provider, this handler will:
1. Extract user attributes (email, name, provider ID) from the `OAuth2User`.
2. Provision/find the user inside our internal database.
3. Generate our internal JWT access and refresh tokens.
4. Redirect the user back to the frontend with the tokens (e.g., as query parameters, or via secure cookies).

* **New File to create:** `com.chauhan.authservice.security.OAuth2SuccessHandler`
* **Skeleton Implementation:**
  ```java
  package com.chauhan.authservice.security;

  import com.chauhan.authservice.service.AuthService;
  import jakarta.servlet.ServletException;
  import jakarta.servlet.http.HttpServletRequest;
  import jakarta.servlet.http.HttpServletResponse;
  import lombok.RequiredArgsConstructor;
  import org.springframework.security.core.Authentication;
  import org.springframework.security.oauth2.core.user.OAuth2User;
  import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
  import org.springframework.stereotype.Component;
  import org.springframework.web.util.UriComponentsBuilder;

  import java.io.IOException;

  @Component
  @RequiredArgsConstructor
  public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

      private final JwtUtil jwtUtil;
      // Inject user service or auth service to handle provisioning / token generation

      @Override
      public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                          Authentication authentication) throws IOException, ServletException {
          OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
          
          // 1. Extract email and attributes
          String email = oAuth2User.getAttribute("email");
          // Handle GitHub email (which might be in a different key or require a fallback)

          // 2. Register / Find User & Generate tokens
          // String accessToken = jwtUtil.generateAccessToken(...);
          // String refreshToken = jwtUtil.generateRefreshToken(...);

          // 3. Build Redirect URL targeting frontend callback
          String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/oauth2/redirect")
                  .queryParam("token", "placeholder_access_token")
                  .queryParam("refresh_token", "placeholder_refresh_token")
                  .build().toUriString();

          getRedirectStrategy().sendRedirect(request, response, targetUrl);
      }
  }
  ```

### B. OAuth2 Failure Handler (`OAuth2FailureHandler`)

If the OAuth2 login fails (e.g., user denies permission or provider error), this handler will redirect the user to the frontend error view with an error message parameter.

* **New File to create:** `com.chauhan.authservice.security.OAuth2FailureHandler`
* **Skeleton Implementation:**
  ```java
  package com.chauhan.authservice.security;

  import jakarta.servlet.ServletException;
  import jakarta.servlet.http.HttpServletRequest;
  import jakarta.servlet.http.HttpServletResponse;
  import org.springframework.security.core.AuthenticationException;
  import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
  import org.springframework.stereotype.Component;
  import org.springframework.web.util.UriComponentsBuilder;

  import java.io.IOException;

  @Component
  public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

      @Override
      public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                          AuthenticationException exception) throws IOException, ServletException {
          
          String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/login")
                  .queryParam("error", exception.getLocalizedMessage())
                  .build().toUriString();

          getRedirectStrategy().sendRedirect(request, response, targetUrl);
      }
  }
  ```

---

## 4. Integrate in Security Filter Chain

We will update our Spring Security configuration to recognize OAuth2 endpoints and register our custom handlers.

* **File to modify:** [SecurityConfig.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/config/SecurityConfig.java)
* **Modifications:**
  * Inject `OAuth2SuccessHandler` and `OAuth2FailureHandler`.
  * Update the `securityFilterChain` method to configure `.oauth2Login(...)`:
  ```java
  http
      // ... existing filters
      .oauth2Login(oauth2 -> oauth2
          .successHandler(oAuth2SuccessHandler)
          .failureHandler(oAuth2FailureHandler)
      );
  ```

---

## 5. Verification & Testing

1. **Unit/Integration Tests:** Setup mock tests utilizing `@WebMvcTest` and mock OAuth2 client configurations.
2. **End-to-End Verification:** Verify redirect flows and callback target urls properly pass credentials back to the client app.
