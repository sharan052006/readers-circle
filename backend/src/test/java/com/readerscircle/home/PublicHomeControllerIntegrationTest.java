package com.readerscircle.home;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PublicHomeControllerIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("app.jwt.secret", () -> "public-home-test-secret-at-least-32-chars-long");
  }

  @Autowired
  private TestRestTemplate rest;

  @Test
  @DisplayName("GET /api/public/home returns 200 OK with live platform stats, events, and testimonials without authentication")
  void getHomeData_isPubliclyAccessible() {
    ResponseEntity<PublicHomeDto> response = rest.getForEntity("/api/public/home", PublicHomeDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    PublicHomeDto body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.stats()).isNotNull();
    assertThat(body.stats().citationCount()).isGreaterThanOrEqualTo(1);
    assertThat(body.testimonials()).isNotNull();
    assertThat(body.testimonials()).isNotEmpty();
    assertThat(body.upcomingEvents()).isNotNull();
  }

  @Test
  @DisplayName("GET /api/public/stats returns 200 OK with dynamic counts")
  void getStats_isPubliclyAccessible() {
    ResponseEntity<PublicStatsDto> response = rest.getForEntity("/api/public/stats", PublicStatsDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    PublicStatsDto body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.citationCount()).isGreaterThanOrEqualTo(1);
  }
}
