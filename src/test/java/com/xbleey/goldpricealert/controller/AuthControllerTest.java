package com.xbleey.goldpricealert.controller;

import com.xbleey.goldpricealert.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
}
