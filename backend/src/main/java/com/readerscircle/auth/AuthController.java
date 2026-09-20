package com.readerscircle.auth;

import com.readerscircle.auth.dto.LoginRequest;
import com.readerscircle.auth.dto.RegisterRequest;
import com.readerscircle.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  /** Single-field body; kept nested so the shared dto package stays at 3 files. */
  public record RefreshRequest(@NotBlank String refreshToken) {}

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public TokenResponse register(@Valid @RequestBody RegisterRequest req) {
    return auth.register(req.name(), req.email(), req.password());
  }

  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody LoginRequest req) {
    return auth.login(req.email(), req.password());
  }

  @PostMapping("/refresh")
  public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
    return auth.refresh(req.refreshToken());
  }
}
