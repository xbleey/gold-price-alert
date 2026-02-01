package com.xbleey.goldpricealert.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final int MAX_BODY_LENGTH = 2048;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = wrapRequest(request);
        String method = wrappedRequest.getMethod();
        String path = wrappedRequest.getRequestURI();
        String query = Optional.ofNullable(wrappedRequest.getQueryString()).orElse("-");
        String params = formatParams(wrappedRequest.getParameterMap());
        long startNanos = System.nanoTime();

        log.info("Request start: method={} path={} query={} params={}", method, path, query, params);
        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            String body = resolveBody(wrappedRequest);
            log.info("Request end: method={} path={} durationMs={} params={} body={}",
                    method, path, durationMs, params, body);
        }
    }

    private static ContentCachingRequestWrapper wrapRequest(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper wrapped) {
            return wrapped;
        }
        return new ContentCachingRequestWrapper(request, MAX_BODY_LENGTH);
    }

    private static String formatParams(Map<String, String[]> params) {
        if (params == null || params.isEmpty()) {
            return "-";
        }
        return params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + Arrays.toString(entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private static String resolveBody(ContentCachingRequestWrapper request) {
        if (!isBodyLoggable(request)) {
            return "-";
        }
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return "-";
        }
        int length = Math.min(content.length, MAX_BODY_LENGTH);
        Charset charset = resolveCharset(request);
        String payload = new String(content, 0, length, charset);
        payload = payload.replaceAll("\\s+", " ").trim();
        if (content.length > MAX_BODY_LENGTH) {
            payload = payload + "...(" + content.length + " bytes)";
        }
        return payload.isEmpty() ? "-" : payload;
    }

    private static Charset resolveCharset(ContentCachingRequestWrapper request) {
        String encoding = Optional.ofNullable(request.getCharacterEncoding())
                .orElse(StandardCharsets.UTF_8.name());
        try {
            return Charset.forName(encoding);
        } catch (Exception ex) {
            return StandardCharsets.UTF_8;
        }
    }

    private static boolean isBodyLoggable(ContentCachingRequestWrapper request) {
        String contentType = request.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        if (contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        if (contentType.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
            return true;
        }
        return contentType.startsWith("text/");
    }
}
