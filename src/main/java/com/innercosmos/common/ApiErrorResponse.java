package com.innercosmos.common;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Stable, versioned error envelope shared by MVC advice and boundary filters. */
public record ApiErrorResponse(
        boolean success,
        String code,
        String message,
        int status,
        String traceId,
        String timestamp,
        Map<String, Object> details) {

    public static ApiErrorResponse of(String code, String message, int status) {
        return of(code, message, status, Map.of());
    }

    public static ApiErrorResponse of(String code, String message, int status, Map<String, Object> details) {
        return new ApiErrorResponse(false, code, message == null ? "" : message, status,
                UUID.randomUUID().toString().replace("-", ""), Instant.now().toString(),
                details == null ? Map.of() : Map.copyOf(details));
    }
}
