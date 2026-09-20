package com.readerscircle.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.readerscircle.auth.dto.LoginRequest;
import com.readerscircle.auth.dto.TokenResponse;
import com.readerscircle.auth.dto.UpdateRoleRequest;
import com.readerscircle.auth.dto.UserDto;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Task 5 gate: Admin CRUD + role matrix (Reader/Organizer × 4 endpoints → 403, no token → 401)
 * + error shapes (400/404). Full HTTP stack on real Postgres + Flyway V1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserAdminIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("app.jwt.secret", () -> "user-admin-test-secret-at-least-32-chars");
  }

  @Autowired TestRestTemplate rest;
  @Autowired UserService users;
  @Autowired UserRepository userRepository;
  @Autowired JwtService jwt;

  private String adminToken;
  private String readerToken;
  private String organizerToken;
  private UUID readerId;

  @BeforeEach
  void seed() {
    userRepository.deleteAll(); // same container DB shared across methods in this class
    users.create("Admin", "admin@x.com", "password123", Role.ADMIN);
    readerId = users.create("Reader", "reader@x.com", "password123", Role.READER).getId();
    users.create("Org", "org@x.com", "password123", Role.ORGANIZER);
    adminToken = login("admin@x.com");
    readerToken = login("reader@x.com");
    organizerToken = login("org@x.com");
  }

  @Test
  void adminCrudAndPromotionImmediate() {
    // List contains the seeded reader.
    var list = get("/api/users", adminToken, new ParameterizedTypeReference<Map<String, Object>>() {});
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(list.getBody().get("content").toString()).contains("reader@x.com");

    // Get + promote READER → ORGANIZER.
    var before = get("/api/users/" + readerId, adminToken, UserDto.class);
    assertThat(before.getBody().role()).isEqualTo(Role.READER);

    var patched =
        rest.exchange(
            "/api/users/" + readerId,
            HttpMethod.PATCH,
            new HttpEntity<>(new UpdateRoleRequest(Role.ORGANIZER), bearer(adminToken)),
            UserDto.class);
    assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(patched.getBody().role()).isEqualTo(Role.ORGANIZER);

    // Fresh login mints the new role — no stale claims.
    assertThat(jwt.extractRole(login("reader@x.com"))).isEqualTo(Role.ORGANIZER);

    // Soft-deactivate: row kept, login blocked.
    var del =
        rest.exchange(
            "/api/users/" + readerId,
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(adminToken)),
            String.class);
    assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(get("/api/users/" + readerId, adminToken, UserDto.class).getBody().active())
        .isFalse();
    assertThat(
            rest.postForEntity(
                    "/api/auth/login",
                    new LoginRequest("reader@x.com", "password123"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void roleMatrixReaderAndOrganizerAre403AnonymousIs401() {
    String[] nonAdmins = {readerToken, organizerToken};
    for (String token : nonAdmins) {
      assertThat(get("/api/users", token, String.class).getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(get("/api/users/" + readerId, token, String.class).getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(
              rest.exchange(
                      "/api/users/" + readerId,
                      HttpMethod.PATCH,
                      new HttpEntity<>(new UpdateRoleRequest(Role.ADMIN), bearer(token)),
                      String.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(
              rest.exchange(
                      "/api/users/" + readerId,
                      HttpMethod.DELETE,
                      new HttpEntity<>(bearer(token)),
                      String.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(get("/api/users", null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(get("/api/users/" + readerId, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void validationIs400MissingIs404WithBodies() {
    var badRole =
        rest.exchange(
            "/api/users/" + readerId,
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of(), bearer(adminToken)),
            Map.class);
    assertThat(badRole.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(badRole.getBody().get("message").toString()).contains("validation");

    UUID ghost = UUID.randomUUID();
    var missing = get("/api/users/" + ghost, adminToken, Map.class);
    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(missing.getBody().get("message").toString()).contains("user not found");

    var patchGhost =
        rest.exchange(
            "/api/users/" + ghost,
            HttpMethod.PATCH,
            new HttpEntity<>(new UpdateRoleRequest(Role.READER), bearer(adminToken)),
            Map.class);
    assertThat(patchGhost.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private String login(String email) {
    var res =
        rest.postForEntity(
            "/api/auth/login", new LoginRequest(email, "password123"), TokenResponse.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return res.getBody().accessToken();
  }

  private <T> ResponseEntity<T> get(
      String path, String token, ParameterizedTypeReference<T> type) {
    return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), type);
  }

  private <T> ResponseEntity<T> get(String path, String token, Class<T> type) {
    return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), type);
  }

  private static HttpHeaders bearer(String token) {
    var h = new HttpHeaders();
    if (token != null) {
      h.setBearerAuth(token);
    }
    return h;
  }
}
