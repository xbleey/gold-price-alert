package com.xbleey.goldpricealert.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderAwareAuthenticationEntryPointTest {

    @Test
    void shouldReturnUnauthorizedWhenAuthorizationHeaderPresent() throws Exception {
        HeaderAwareAuthenticationEntryPoint entryPoint = new HeaderAwareAuthenticationEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("bad credentials"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer realm=\"gold-price-alert\"");
    }

    @Test
    void shouldReturnUnauthorizedWithChallengeWhenAuthorizationHeaderMissing() throws Exception {
        HeaderAwareAuthenticationEntryPoint entryPoint = new HeaderAwareAuthenticationEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("missing credentials"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer realm=\"gold-price-alert\"");
    }
}
