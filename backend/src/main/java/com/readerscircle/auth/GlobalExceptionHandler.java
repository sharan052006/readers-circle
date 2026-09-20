package com.readerscircle.auth;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Central error bodies (Task 5). Statuses are the contract — this handler only shapes bodies:
 * validation→400, unauthenticated→401, wrong role→403, missing→404, conflict→409.
 * {@link ResponseStatusException} (thrown by Task 4 services) keeps its status; only the body
 * is normalized here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  public record ErrorBody(int status, String message, Map<String, String> errors) {
    static ErrorBody of(HttpStatus status, String message) {
      return new ErrorBody(status.value(), message, null);
    }
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex) {
    Map<String, String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    FieldError::getField,
                    f -> f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage(),
                    (a, b) -> a));
    return ResponseEntity.badRequest().body(new ErrorBody(400, "validation failed", errors));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ErrorBody> status(ResponseStatusException ex) {
    HttpStatus status =
        ex.getStatusCode() instanceof HttpStatus h ? h : HttpStatus.INTERNAL_SERVER_ERROR;
    String message = ex.getReason() == null ? status.getReasonPhrase() : ex.getReason();
    return ResponseEntity.status(status).body(ErrorBody.of(status, message));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorBody> denied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ErrorBody.of(HttpStatus.FORBIDDEN, "forbidden"));
  }

  @ExceptionHandler({NoSuchElementException.class})
  ResponseEntity<ErrorBody> missing(RuntimeException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorBody.of(HttpStatus.NOT_FOUND, "not found"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorBody> conflict(DataIntegrityViolationException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorBody.of(HttpStatus.CONFLICT, "conflict"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorBody> unexpected(Exception ex) {
    log.error("Unhandled error", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorBody.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal error"));
  }
}
