package com.xbleey.goldpricealert.controller;

import com.xbleey.goldpricealert.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthSessionService authSessionService;

    public AuthController(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) LoginRequest request) {
        if (request == null) {
            return badRequest("request body must not be null");
        }
        try {
            AuthSessionService.AuthSession session = authSessionService.login(request.username(), request.password());
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "tokenType", "Bearer",
                    "accessToken", session.token(),
                    "expiresAt", session.expiresAt(),
                    "username", session.username(),
                    "role", session.role()
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (AuthenticationException ex) {
            return unauthorized("invalid username or password");
        } catch (IllegalStateException ex) {
            return response(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "login failed");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        String token = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "logged_out",
                    "revoked", false
            ));
        }
        try {
            boolean revoked = authSessionService.logout(token);
            return ResponseEntity.ok(Map.of(
                    "status", "logged_out",
                    "revoked", revoked
            ));
        } catch (IllegalStateException ex) {
            return response(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "logout failed");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return unauthorized("invalid or expired token");
        }
        String token = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            return unauthorized("invalid or expired token");
        }
        return authSessionService.getSession(token)
                .map(session -> ResponseEntity.ok(Map.of(
                        "username", session.username(),
                        "role", session.role(),
                        "authorities", extractAuthorities(authentication),
                        "expiresAt", session.expiresAt()
                )))
                .orElseGet(() -> unauthorized("invalid or expired token"));
    }

    private List<String> extractAuthorities(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return null;
        }
        return token;
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return response(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    private ResponseEntity<Map<String, Object>> unauthorized(String message) {
        return response(HttpStatus.UNAUTHORIZED, "unauthorized", message);
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    public record LoginRequest(String username, String password) {
    }
}
