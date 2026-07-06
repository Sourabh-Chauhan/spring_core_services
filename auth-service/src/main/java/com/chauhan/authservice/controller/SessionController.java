package com.chauhan.authservice.controller;

import com.chauhan.authservice.dto.response.SessionResponse;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.security.JwtUtil;
import com.chauhan.authservice.service.impl.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<SessionResponse>> getActiveSessions(
            @AuthenticationPrincipal User user, 
            HttpServletRequest request
    ) {
        String currentJti = extractJtiFromRequest(request);
        List<SessionResponse> sessions = refreshTokenService.getActiveSessions(user.getId(), currentJti);
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @PathVariable("sessionId") UUID sessionId, 
            @AuthenticationPrincipal User user
    ) {
        refreshTokenService.revokeSession(sessionId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/other")
    public ResponseEntity<Void> revokeOtherSessions(
            @AuthenticationPrincipal User user, 
            HttpServletRequest request
    ) {
        String currentJti = extractJtiFromRequest(request);
        refreshTokenService.revokeAllSessionsExcept(user.getId(), currentJti);
        return ResponseEntity.noContent().build();
    }

    private String extractJtiFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authHeader.substring(7).trim();
            try {
                return jwtUtil.getJti(token);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
