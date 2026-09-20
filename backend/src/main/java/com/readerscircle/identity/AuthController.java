package com.readerscircle.identity;

import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
    AuthResponse res = auth.registerReader(req);
    return ResponseEntity.status(HttpStatus.CREATED)
        .header("Set-Cookie", refreshCookie(res))
        .body(res);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
    AuthResponse res = auth.login(req);
    return ResponseEntity.ok().header("Set-Cookie", refreshCookie(res)).body(res);
  }

  public record RefreshRequest(String refreshToken) {}

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(
      @RequestBody(required = false) RefreshRequest body,
      @CookieValue(name = "refreshToken", required = false) String cookie) {
    String token = (body != null && body.refreshToken() != null) ? body.refreshToken() : cookie;
    if (token == null || token.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    AuthResponse res = auth.refresh(token);
    return ResponseEntity.ok().header("Set-Cookie", refreshCookie(res)).body(res);
  }

  private String refreshCookie(AuthResponse res) {
    ResponseCookie cookie =
        ResponseCookie.from("refreshToken", res.refreshToken())
            .httpOnly(true)
            .path("/api/auth")
            .maxAge(Duration.ofDays(7))
            .sameSite("Lax")
            .build();
    return cookie.toString();
  }
}
