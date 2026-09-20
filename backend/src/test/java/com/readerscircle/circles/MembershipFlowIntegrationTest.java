package com.readerscircle.circles;

import static org.assertj.core.api.Assertions.assertThat;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.auth.UserService;
import com.readerscircle.circles.dto.CircleDetailDto;
import com.readerscircle.circles.dto.CircleSummaryDto;
import com.readerscircle.circles.dto.CreateCircleRequest;
import com.readerscircle.circles.dto.JoinActionRequest;
import com.readerscircle.circles.dto.MemberDto;
import com.readerscircle.circles.dto.MembershipDto;
import java.util.List;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MembershipFlowIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("app.jwt.secret", () -> "membership-flow-integration-test-secret-32-chars");
  }

  @Autowired TestRestTemplate rest;
  @Autowired CircleRepository circleRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired UserRepository userRepository;
  @Autowired UserService userService;
  @Autowired com.readerscircle.auth.AuthService authService;

  private String adminToken;
  private String organizerToken;
  private String otherOrganizerToken;
  private String readerToken;
  private UUID organizerId;
  private UUID otherOrganizerId;
  private UUID readerId;

  @BeforeEach
  void seed() {
    membershipRepository.deleteAll();
    circleRepository.deleteAll();
    userRepository.deleteAll();

    User admin = userService.create("Admin", "admin@test.com", "pass123456", Role.ADMIN);
    User organizer = userService.create("Organizer 1", "org1@test.com", "pass123456", Role.ORGANIZER);
    User otherOrganizer = userService.create("Organizer 2", "org2@test.com", "pass123456", Role.ORGANIZER);
    User reader = userService.create("Reader One", "reader1@test.com", "pass123456", Role.READER);

    organizerId = organizer.getId();
    otherOrganizerId = otherOrganizer.getId();
    readerId = reader.getId();

    adminToken = authService.login("admin@test.com", "pass123456").accessToken();
    organizerToken = authService.login("org1@test.com", "pass123456").accessToken();
    otherOrganizerToken = authService.login("org2@test.com", "pass123456").accessToken();
    readerToken = authService.login("reader1@test.com", "pass123456").accessToken();
  }

  private HttpHeaders authHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  @Test
  void fullMembershipLifecycleAndSecurityChecks() {
    // 1. Admin creates circle
    CreateCircleRequest createReq =
        new CreateCircleRequest("Dhaka Classics", "Dhaka", "Classics book club in Dhaka", organizerId);
    ResponseEntity<CircleDetailDto> createRes =
        rest.exchange(
            "/api/circles",
            HttpMethod.POST,
            new HttpEntity<>(createReq, authHeaders(adminToken)),
            CircleDetailDto.class);
    assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UUID circleId = createRes.getBody().id();

    // 2. Public circle discovery by city
    ResponseEntity<List<CircleSummaryDto>> listRes =
        rest.exchange(
            "/api/circles?city=dhaka",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(listRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listRes.getBody()).hasSize(1);
    assertThat(listRes.getBody().get(0).name()).isEqualTo("Dhaka Classics");

    // 3. Reader requests to join circle -> 201 CREATED
    ResponseEntity<MembershipDto> joinRes =
        rest.exchange(
            "/api/circles/" + circleId + "/join-requests",
            HttpMethod.POST,
            new HttpEntity<>(null, authHeaders(readerToken)),
            MembershipDto.class);
    assertThat(joinRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(joinRes.getBody().status()).isEqualTo(MembershipStatus.PENDING);
    UUID membershipId = joinRes.getBody().id();

    // 4. Second join request from same reader while PENDING -> 409 CONFLICT
    ResponseEntity<String> dupRes =
        rest.exchange(
            "/api/circles/" + circleId + "/join-requests",
            HttpMethod.POST,
            new HttpEntity<>(null, authHeaders(readerToken)),
            String.class);
    assertThat(dupRes.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    // 5. Foreign organizer attempts to approve request -> 403 FORBIDDEN
    JoinActionRequest approveAction = new JoinActionRequest("APPROVE");
    ResponseEntity<String> foreignRes =
        rest.exchange(
            "/api/join-requests/" + membershipId,
            HttpMethod.PATCH,
            new HttpEntity<>(approveAction, authHeaders(otherOrganizerToken)),
            String.class);
    assertThat(foreignRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // 6. Assigned organizer approves the request -> 200 OK
    ResponseEntity<MemberDto> approveRes =
        rest.exchange(
            "/api/join-requests/" + membershipId,
            HttpMethod.PATCH,
            new HttpEntity<>(approveAction, authHeaders(organizerToken)),
            MemberDto.class);
    assertThat(approveRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(approveRes.getBody().status()).isEqualTo(MembershipStatus.APPROVED);

    // 7. Approved member appears in member list
    ResponseEntity<List<MemberDto>> membersRes =
        rest.exchange(
            "/api/circles/" + circleId + "/members",
            HttpMethod.GET,
            new HttpEntity<>(null, authHeaders(organizerToken)),
            new ParameterizedTypeReference<>() {});
    assertThat(membersRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(membersRes.getBody().stream().anyMatch(m -> m.readerId().equals(readerId))).isTrue();

    // 8. Admin deactivates circle -> 204
    ResponseEntity<Void> deactivateRes =
        rest.exchange(
            "/api/circles/" + circleId,
            HttpMethod.DELETE,
            new HttpEntity<>(null, authHeaders(adminToken)),
            Void.class);
    assertThat(deactivateRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // 9. Public GET on deactivated circle -> 404 NOT FOUND
    ResponseEntity<String> publicReadRes =
        rest.exchange("/api/circles/" + circleId, HttpMethod.GET, null, String.class);
    assertThat(publicReadRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
