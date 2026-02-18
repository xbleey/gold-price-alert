package com.xbleey.goldpricealert.controller;

import com.xbleey.goldpricealert.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void loginReturnsTokenWhenCredentialsValid() {
        AuthSessionService authSessionService = mock(AuthSessionService.class);
        AuthSessionService.AuthSession session = new AuthSessionService.AuthSession(
                "token-1",
                "admin",
                "ADMIN",
                Instant.parse("2026-02-18T12:00:00Z"),
                Instant.parse("2026-02-19T00:00:00Z")
        );
        when(authSessionService.login("admin", "admin")).thenReturn(session);
        AuthController controller = new AuthController(authSessionService);

        ResponseEntity<Map<String, Object>> response = controller.login(new AuthController.LoginRequest("admin", "admin"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "ok");
        assertThat(response.getBody()).containsEntry("tokenType", "Bearer");
        assertThat(response.getBody()).containsEntry("accessToken", "token-1");
    }

    @Test
    void loginReturnsUnauthorizedWhenAuthenticationFails() {
        AuthSessionService authSessionService = mock(AuthSessionService.class);
        when(authSessionService.login("admin", "bad-password")).thenThrow(new BadCredentialsException("bad credentials"));
        AuthController controller = new AuthController(authSessionService);

        ResponseEntity<Map<String, Object>> response = controller.login(new AuthController.LoginRequest("admin", "bad-password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("status", "unauthorized");
    }

    @Test
    void logoutDeletesSessionFromRedis() {
        AuthSessionService authSessionService = mock(AuthSessionService.class);
        when(authSessionService.logout("token-2")).thenReturn(true);
        AuthController controller = new AuthController(authSessionService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-2");

        ResponseEntity<Map<String, Object>> response = controller.logout(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "logged_out");
        assertThat(response.getBody()).containsEntry("revoked", true);
        verify(authSessionService).logout("token-2");
    }

    @Test
    void logoutShouldBeIdempotentWhenTokenMissing() {
        AuthSessionService authSessionService = mock(AuthSessionService.class);
        AuthController controller = new AuthController(authSessionService);
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<Map<String, Object>> response = controller.logout(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "logged_out");
        assertThat(response.getBody()).containsEntry("revoked", false);
        verifyNoInteractions(authSessionService);
    }

    @Test
    void meReturnsCurrentUserContext() {
        AuthSessionService authSessionService = mock(AuthSessionService.class);
        AuthSessionService.AuthSession session = new AuthSessionService.AuthSession(
                "token-3",
                "admin",
                "ADMIN",
                Instant.parse("2026-02-18T12:00:00Z"),
                Instant.parse("2026-02-19T00:00:00Z")
        );
        when(authSessionService.getSession("token-3")).thenReturn(Optional.of(session));
        AuthController controller = new AuthController(authSessionService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-3");
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        ResponseEntity<Map<String, Object>> response = controller.me(request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("username", "admin");
        assertThat(response.getBody()).containsEntry("role", "ADMIN");
        assertThat(response.getBody()).containsEntry("expiresAt", Instant.parse("2026-02-19T00:00:00Z"));
        assertThat(response.getBody()).containsEntry("authorities", List.of("ROLE_ADMIN"));
    }

    @Test
    void meReturnsUnauthorizedWhenSessionMissing() {
        AuthSessionService authSessionService = mock(AuthSessionService.class);
        when(authSessionService.getSession("token-missing")).thenReturn(Optional.empty());
        AuthController controller = new AuthController(authSessionService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-missing");
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        ResponseEntity<Map<String, Object>> response = controller.me(request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("status", "unauthorized");
    }
}
