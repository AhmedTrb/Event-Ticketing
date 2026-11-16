package com.backend.ticketingapi.config;

import com.backend.shared.exceptions.BusinessException;
import com.backend.shared.exceptions.SeatAlreadyBookedException;
import com.backend.shared.exceptions.SeatLockExpiredException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps domain exceptions to meaningful HTTP responses
 * (e.g. 409 Conflict when seats are taken, as documented in the README).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(SeatAlreadyBookedException.class)
    public ResponseEntity<Map<String, Object>> handleSeatTaken(SeatAlreadyBookedException ex) {
        return body(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getUserMessage());
    }

    @ExceptionHandler(SeatLockExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleLockExpired(SeatLockExpiredException ex) {
        return body(HttpStatus.GONE, ex.getErrorCode(), ex.getUserMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        HttpStatus status = ex.getErrorCode().endsWith("_NOT_FOUND")
                ? HttpStatus.NOT_FOUND
                : ex.getErrorCode().endsWith("_EXISTS") ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return body(status, ex.getErrorCode(), ex.getUserMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status.value());
        payload.put("error", code);
        payload.put("message", message);
        return ResponseEntity.status(status).body(payload);
    }
}
