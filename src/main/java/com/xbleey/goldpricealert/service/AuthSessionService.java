package com.xbleey.goldpricealert.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthSessionService {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionService.class);
    private static final String SESSION_KEY_PREFIX = "gold:auth:session:";

    private final AuthenticationManager authenticationManager;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration sessionTtl;

    public AuthSessionService(
            AuthenticationManager authenticationManager,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${gold.auth.session-ttl:12h}") Duration sessionTtl
    ) {
        this.authenticationManager = authenticationManager;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.sessionTtl = validateSessionTtl(sessionTtl);
    }

    public AuthSession login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        String validatedPassword = validatePassword(password);
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(normalizedUsername, validatedPassword)
        );

        Instant loginAt = clock.instant();
        Instant expiresAt = loginAt.plus(sessionTtl);
        String token = UUID.randomUUID().toString().replace("-", "");
        String role = resolveRole(authentication.getAuthorities());
        String authenticatedUsername = authentication.getName();
        SessionState state = new SessionState(authenticatedUsername, role, loginAt, expiresAt);
        persistSession(token, state);
        return new AuthSession(token, authenticatedUsername, role, loginAt, expiresAt);
    }

    public Optional<AuthSession> getSession(String token) {
        String normalizedToken = normalizeToken(token);
        if (normalizedToken == null) {
            return Optional.empty();
        }
        try {
            String cached = redisTemplate.opsForValue().get(sessionKey(normalizedToken));
            if (cached == null || cached.isBlank()) {
                return Optional.empty();
            }
            SessionState state = objectMapper.readValue(cached, SessionState.class);
            return Optional.of(new AuthSession(
                    normalizedToken,
                    state.username(),
                    state.role(),
                    state.loginAt(),
                    state.expiresAt()
            ));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse auth session from redis", ex);
            redisTemplate.delete(sessionKey(normalizedToken));
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Failed to read auth session from redis", ex);
            return Optional.empty();
        }
    }

    public boolean logout(String token) {
        String normalizedToken = normalizeToken(token);
        if (normalizedToken == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(sessionKey(normalizedToken)));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to clear auth session from redis", ex);
        }
    }

    public Authentication toAuthentication(AuthSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + normalizeRole(session.role()))
        );
        return UsernamePasswordAuthenticationToken.authenticated(session.username(), null, authorities);
    }

    private void persistSession(String token, SessionState state) {
        try {
            redisTemplate.opsForValue().set(
                    sessionKey(token),
                    objectMapper.writeValueAsString(state),
                    sessionTtl
            );
        } catch (Exception ex) {
            throw new IllegalStateException("failed to persist auth session to redis", ex);
        }
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        return password;
    }

    private String normalizeToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    private String resolveRole(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return "USER";
        }
        for (GrantedAuthority authority : authorities) {
            if (authority == null) {
                continue;
            }
            String value = authority.getAuthority();
            if (value != null && value.startsWith("ROLE_") && value.length() > "ROLE_".length()) {
                return value.substring("ROLE_".length()).toUpperCase(Locale.ROOT);
            }
        }
        return "USER";
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "USER";
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private String sessionKey(String token) {
        return SESSION_KEY_PREFIX + token;
    }

    private Duration validateSessionTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("gold.auth.session-ttl must be > 0");
        }
        return ttl;
    }

    private record SessionState(
            String username,
            String role,
            Instant loginAt,
            Instant expiresAt
    ) {
    }

    public record AuthSession(
            String token,
            String username,
            String role,
            Instant loginAt,
            Instant expiresAt
    ) {
    }
}
