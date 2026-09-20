package com.readerscircle.identity;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> handleRse(ResponseStatusException e) {
    HttpStatus status =
        e.getStatusCode() instanceof HttpStatus hs ? hs : HttpStatus.INTERNAL_SERVER_ERROR;
    String code = status.name().replace(' ', '_');
    return ResponseEntity.status(status).body(Map.of("code", code, "message", e.getReason()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
    String msg =
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .findFirst()
            .orElse("validation failed");
    return ResponseEntity.badRequest().body(Map.of("code", "BAD_REQUEST", "message", msg));
  }
}
