package com.xbleey.goldpricealert.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderAwareAuthenticationEntryPointTest {

    @Test
    void shouldReturnForbiddenWhenAuthorizationHeaderPresent() throws Exception {
        HeaderAwareAuthenticationEntryPoint entryPoint = new HeaderAwareAuthenticationEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("bad credentials"));

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldReturnUnauthorizedWithChallengeWhenAuthorizationHeaderMissing() throws Exception {
        HeaderAwareAuthenticationEntryPoint entryPoint = new HeaderAwareAuthenticationEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("missing credentials"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Basic realm=\"gold-price-alert\"");
    }
}
