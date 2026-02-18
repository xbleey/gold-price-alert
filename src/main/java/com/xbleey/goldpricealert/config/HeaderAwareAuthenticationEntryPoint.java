package com.xbleey.goldpricealert.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class HeaderAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String BEARER_CHALLENGE = "Bearer realm=\"gold-price-alert\"";

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
