package com.xbleey.goldpricealert.web;

import com.xbleey.goldpricealert.service.AuthSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BearerTokenAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSetSecurityContextWhenTokenExistsInRedis() throws Exception {
        AuthSessionService authSessionService = mock(AuthSessionService.class);
        AuthSessionService.AuthSession session = new AuthSessionService.AuthSession(
                "token-1",
                "admin",
                "ADMIN",
                Instant.parse("2026-02-18T12:00:00Z"),
                Instant.parse("2026-02-19T00:00:00Z")
        );
        when(authSessionService.getSession("token-1")).thenReturn(Optional.of(session));
        when(authSessionService.toAuthentication(session)).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of())
        );
        BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(authSessionService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin");
    }
}
