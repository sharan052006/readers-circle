package com.readerscircle.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoint + branch coverage for the identity module: refresh endpoint,
 * admin user lifecycle, validation envelope, bearer edge cases, and
 * refresh-token expiry / deactivation branches. Read-only additions —
 * no API contract changes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityCoverageTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;
  @Autowired AuthService auth;
  @Autowired UserRepository users;
  @Autowired RefreshTokenRepository refreshTokens;
  @Autowired JwtService jwt;
  @Autowired BootstrapAdmin bootstrapAdmin;

  private AuthResponse register(String prefix) {
    String email = prefix + System.nanoTime() + "@t.com";
    return auth.registerReader(new RegisterRequest(prefix, email, "password123"));
  }

  private void promote(UUID id) {
    User u = users.findById(id).orElseThrow();
    u.setRole(Role.ADMIN);
    users.save(u);
  }

  private String adminToken() {
    AuthResponse admin = register("adm");
    promote(admin.userId());
    return jwt.generateAccess(users.findById(admin.userId()).orElseThrow());
  }

  @Test
  void refreshEndpointRotatesAndRejectsEmpty() throws Exception {
    AuthResponse login = register("ref");
    String body = om.writeValueAsString(Map.of("refreshToken", login.refreshToken()));

    MvcResult ok =
        mvc.perform(
                post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn();
    Map<?, ?> pair = om.readValue(ok.getResponse().getContentAsString(), Map.class);
    assertThat(pair.get("accessToken")).isNotNull();

    mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
  }

  @Test
  void adminUserLifecycle() throws Exception {
    String admin = adminToken();
    AuthResponse target = register("tgt");

    mvc.perform(get("/api/users").header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk());

    mvc.perform(
            get("/api/users/" + UUID.randomUUID()).header("Authorization", "Bearer " + admin))
        .andExpect(status().isNotFound());

    mvc.perform(
            patch("/api/users/" + target.userId())
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"NOPE\"}"))
        .andExpect(status().isBadRequest());

    mvc.perform(
            patch("/api/users/" + target.userId())
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\"}"))
        .andExpect(status().isBadRequest());

    mvc.perform(
            delete("/api/users/" + target.userId()).header("Authorization", "Bearer " + admin))
        .andExpect(status().isNoContent());

    User deactivated = users.findById(target.userId()).orElseThrow();
    assertThat(deactivated.isDeactivated()).isTrue();
  }

  @Test
  void registerValidationFails400() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"email\":\"bad\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void invalidAndExpiredBearer401() throws Exception {
    mvc.perform(get("/api/users/me").header("Authorization", "Bearer garbage.token.here"))
        .andExpect(status().isUnauthorized());

    JwtService zeroLife =
        new JwtService("test-only-secret-1234567890abcdef-test", 0, 0);
    AuthResponse r = register("exp");
    String expired =
        zeroLife.generateAccess(users.findById(r.userId()).orElseThrow());
    Thread.sleep(1100);
    mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshAfterDeactivation401() {
    AuthResponse login = register("deact");
    User u = users.findById(login.userId()).orElseThrow();
    u.setDeactivated(true);
    users.save(u);
    assertThatThrownBy(() -> auth.refresh(login.refreshToken()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void refreshWithExpiredRow401() {
    AuthResponse login = register("old");
    RefreshToken row =
        refreshTokens.findByTokenHash(AuthService.sha256(login.refreshToken())).orElseThrow();
    refreshTokens.delete(row);
    refreshTokens.save(
        new RefreshToken(
            login.userId(), row.getTokenHash(), OffsetDateTime.now().minusHours(1)));
    // The failed refresh rolls back, so we only assert the 401 outcome here.
    assertThatThrownBy(() -> auth.refresh(login.refreshToken()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void bootstrapAdminIsIdempotent() {
    bootstrapAdmin.run();
    bootstrapAdmin.run();
    assertThat(users.existsByEmailIgnoreCase("admin@readers.local")).isTrue();
  }
}
