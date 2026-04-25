package com.xbleey.goldpricealert.service;

import org.springframework.http.HttpStatus;

public class AiChatException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String statusCode;

    private AiChatException(HttpStatus httpStatus, String statusCode, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.statusCode = statusCode;
    }

    public static AiChatException badRequest(String message) {
        return new AiChatException(HttpStatus.BAD_REQUEST, "bad_request", message, null);
    }

    public static AiChatException notFound(String message) {
        return new AiChatException(HttpStatus.NOT_FOUND, "not_found", message, null);
    }

    public static AiChatException unavailable(String message) {
        return new AiChatException(HttpStatus.SERVICE_UNAVAILABLE, "ai_unavailable", message, null);
    }

    public static AiChatException upstreamUnavailable(String message, Throwable cause) {
        return new AiChatException(HttpStatus.SERVICE_UNAVAILABLE, "upstream_unavailable", message, cause);
    }

    public static AiChatException upstreamError(String message, Throwable cause) {
        return new AiChatException(HttpStatus.BAD_GATEWAY, "upstream_error", message, cause);
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String statusCode() {
        return statusCode;
    }
}
