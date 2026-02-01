package com.xbleey.goldpricealert.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "trace.id";
    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final Pattern TRACEPARENT_PATTERN = Pattern.compile(
            "^[\\da-f]{2}-([\\da-f]{32})-[\\da-f]{16}-[\\da-f]{2}$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String traceParent = request.getHeader(TRACEPARENT_HEADER);
        if (traceParent != null && !traceParent.isBlank()) {
            Matcher matcher = TRACEPARENT_PATTERN.matcher(traceParent.trim());
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        String headerTraceId = request.getHeader(TRACE_ID_HEADER);
        if (headerTraceId != null && !headerTraceId.isBlank()) {
            return headerTraceId.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
