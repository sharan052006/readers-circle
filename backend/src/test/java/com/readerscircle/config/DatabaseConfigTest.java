package com.readerscircle.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseConfigTest {

  @Test
  void testStandardJdbcUrlPassedThrough() {
    String url = "jdbc:postgresql://dpg-dbas3jk9v7es739o749g-a:5432/readers_circle_db";
    DatabaseConfig.ParsedConnection conn = DatabaseConfig.parseConnection(url, "user1", "pass1");
    assertThat(conn.jdbcUrl()).isEqualTo(url);
    assertThat(conn.username()).isEqualTo("user1");
    assertThat(conn.password()).isEqualTo("pass1");
  }

  @Test
  void testPostgresqlSchemeNormalizedToJdbc() {
    String url = "postgresql://dpg-dbas3jk9v7es739o749g-a:5432/readers_circle_db";
    DatabaseConfig.ParsedConnection conn = DatabaseConfig.parseConnection(url, "user1", "pass1");
    assertThat(conn.jdbcUrl()).isEqualTo("jdbc:postgresql://dpg-dbas3jk9v7es739o749g-a:5432/readers_circle_db");
    assertThat(conn.username()).isEqualTo("user1");
    assertThat(conn.password()).isEqualTo("pass1");
    assertThat(conn.driverClassName()).isEqualTo("org.postgresql.Driver");
  }

  @Test
  void testRenderUriWithCredentialsExtracted() {
    String url = "postgresql://readers_circle_db_user:secretPass123@dpg-dbas3jk9v7es739o749g-a:5432/readers_circle_db";
    DatabaseConfig.ParsedConnection conn = DatabaseConfig.parseConnection(url, "defaultUser", "defaultPass");
    assertThat(conn.jdbcUrl()).isEqualTo("jdbc:postgresql://dpg-dbas3jk9v7es739o749g-a:5432/readers_circle_db");
    assertThat(conn.username()).isEqualTo("readers_circle_db_user");
    assertThat(conn.password()).isEqualTo("secretPass123");
  }

  @Test
  void testPostgresSchemeNormalized() {
    String url = "postgres://dpg-dbas3jk9v7es739o749g-a:5432/readers_circle_db";
    DatabaseConfig.ParsedConnection conn = DatabaseConfig.parseConnection(url, "user1", "pass1");
    assertThat(conn.jdbcUrl()).isEqualTo("jdbc:postgresql://dpg-dbas3jk9v7es739o749g-a:5432/readers_circle_db");
  }

  @Test
  void testH2UrlPreserved() {
    String url = "jdbc:h2:file:./data/readerscircle";
    DatabaseConfig.ParsedConnection conn = DatabaseConfig.parseConnection(url, "sa", "");
    assertThat(conn.jdbcUrl()).isEqualTo(url);
    assertThat(conn.username()).isEqualTo("sa");
  }
}
