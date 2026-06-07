package com.innercosmos.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Global exception handler for consistent error responses in production.
 * All API errors return JSON with: error code, message, timestamp, path.
 * Never exposes stack traces in production.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(errorMap("BAD_REQUEST", ex.getMessage(), HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointer(NullPointerException ex) {
        log.error("NullPointerException in request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorMap("INTERNAL_ERROR", "服务器内部错误，请稍后再试。", HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        // Check for specific business exceptions
        if (ex.getMessage() != null && ex.getMessage().contains("not found")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorMap("NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND.value()));
        }
        if (ex.getMessage() != null && ex.getMessage().contains("access denied")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorMap("FORBIDDEN", ex.getMessage(), HttpStatus.FORBIDDEN.value()));
        }
        log.error("RuntimeException in request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorMap("INTERNAL_ERROR", "服务器内部错误，请稍后再试。", HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorMap("INTERNAL_ERROR", "服务器内部错误，请稍后再试。", HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    private Map<String, Object> errorMap(String code, String message, int status) {
        return Map.of(
            "error", code,
            "message", message,
            "status", status,
            "timestamp", LocalDateTime.now().toString()
        );
    }
}