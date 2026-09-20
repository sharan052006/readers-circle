package com.readerscircle.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Task 1 gate: proves Flyway V1 migration applies + case-insensitive email rule. */
@Testcontainers
class V1MigrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Test
  void v1CreatesUsersWithCaseInsensitiveEmail() throws Exception {
    DataSource ds =
        new SingleConnectionDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true);
    try (Connection c = ds.getConnection()) {
      ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V1__users.sql"));
    }
    JdbcTemplate jdbc = new JdbcTemplate(ds);

    List<String> tables =
        jdbc.queryForList("SELECT tablename FROM pg_tables WHERE tablename = 'users'", String.class);
    assertThat(tables).containsExactly("users");

    Integer idx =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE tablename = 'users' AND indexname = 'ux_users_email_lower'",
            Integer.class);
    assertThat(idx).isEqualTo(1);

    jdbc.update(
        "INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, ?)",
        "A",
        "A@x.com",
        "h",
        "READER");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, ?)",
                    "B",
                    "a@X.com",
                    "h",
                    "READER"))
        .isInstanceOf(DataAccessException.class);
  }
}
