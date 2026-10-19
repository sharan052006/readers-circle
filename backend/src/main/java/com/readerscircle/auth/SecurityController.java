package com.readerscircle.auth;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Security Settings backend: two-factor auth, active sessions, sign out everywhere.
 */
@RestController
@RequestMapping("/api/users/me")
@PreAuthorize("isAuthenticated()")
public class SecurityController {

  private final AuthService auth;

  public SecurityController(AuthService auth) {
    this.auth = auth;
  }

  /** Begin 2FA setup — returns the secret + otpauth URL for the authenticator app. */
  @PostMapping("/2fa/setup")
  public Map<String, Object> setupTwoFactor(Authentication authentication) {
    return auth.setupTwoFactor(getUserId(authentication));
  }

  /** Confirm 2FA setup with a 6-digit code. */
  @PostMapping("/2fa/enable")
  public Map<String, Object> enableTwoFactor(
      Authentication authentication, @RequestBody Map<String, String> body) {
    String code = body == null ? null : body.get("code");
    return auth.enableTwoFactor(getUserId(authentication), code);
  }

  /** Disable 2FA (secret discarded). */
  @DeleteMapping("/2fa")
  public Map<String, Object> disableTwoFactor(Authentication authentication) {
    return auth.disableTwoFactor(getUserId(authentication));
  }

  /** Active Sessions list. */
  @GetMapping("/sessions")
  public List<Map<String, Object>> sessions(Authentication authentication) {
    return auth.listSessions(getUserId(authentication));
  }

  /** Revoke one session. */
  @DeleteMapping("/sessions/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeSession(Authentication authentication, @PathVariable UUID id) {
    auth.revokeSession(getUserId(authentication), id);
  }

  /** Sign out of all devices. */
  @PostMapping("/sign-out-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void signOutAll(Authentication authentication) {
    auth.signOutAll(getUserId(authentication));
  }

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
  }
}
