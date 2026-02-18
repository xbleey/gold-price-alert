package com.xbleey.goldpricealert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-18T12:00:00Z");

    @Test
    void loginShouldPersistSessionToRedis() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(
                "admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated);
        AuthSessionService service = new AuthSessionService(
                authenticationManager,
                redisTemplate,
                objectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(12)
        );

        AuthSessionService.AuthSession session = service.login(" Admin ", "admin");

        ArgumentCaptor<Authentication> authenticationCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authenticationCaptor.capture());
        Authentication captured = authenticationCaptor.getValue();
        assertThat(captured.getName()).isEqualTo("admin");
        assertThat(captured.getCredentials()).isEqualTo("admin");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(ops).set(keyCaptor.capture(), valueCaptor.capture(), eq(Duration.ofHours(12)));
        assertThat(keyCaptor.getValue()).isEqualTo("gold:auth:session:" + session.token());
        assertThat(valueCaptor.getValue()).contains("\"username\":\"admin\"");
        assertThat(session.username()).isEqualTo("admin");
        assertThat(session.role()).isEqualTo("ADMIN");
        assertThat(session.loginAt()).isEqualTo(NOW);
        assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
    }

    @Test
    void getSessionShouldReturnSessionWhenTokenExists() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get("gold:auth:session:token-1")).thenReturn("""
                {"username":"admin","role":"ADMIN","loginAt":"2026-02-18T12:00:00Z","expiresAt":"2026-02-18T13:00:00Z"}
                """);
        AuthSessionService service = new AuthSessionService(
                authenticationManager,
                redisTemplate,
                objectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(12)
        );

        Optional<AuthSessionService.AuthSession> session = service.getSession("token-1");

        assertThat(session).isPresent();
        assertThat(session.get().token()).isEqualTo("token-1");
        assertThat(session.get().username()).isEqualTo("admin");
        assertThat(session.get().role()).isEqualTo("ADMIN");
        assertThat(session.get().loginAt()).isEqualTo(Instant.parse("2026-02-18T12:00:00Z"));
        assertThat(session.get().expiresAt()).isEqualTo(Instant.parse("2026-02-18T13:00:00Z"));
    }

    @Test
    void logoutShouldDeleteRedisSession() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.delete("gold:auth:session:token-2")).thenReturn(true);
        AuthSessionService service = new AuthSessionService(
                authenticationManager,
                redisTemplate,
                objectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(12)
        );

        boolean revoked = service.logout(" token-2 ");

        assertThat(revoked).isTrue();
        verify(redisTemplate).delete("gold:auth:session:token-2");
    }

    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
