package com.readerscircle.config;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Normalizes PostgreSQL connection URLs for Render and container environments.
 * Ensures URLs starting with postgresql:// or postgres:// are converted to valid
 * JDBC URLs (jdbc:postgresql://) and parses embedded credentials if supplied.
 */
@Configuration
public class DatabaseConfig {

  private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

  @Bean
  @Primary
  public DataSource dataSource(DataSourceProperties properties) {
    String rawUrl = properties.determineUrl();

    ParsedConnection conn = parseConnection(
        rawUrl, properties.determineUsername(), properties.determinePassword());

    HikariDataSource dataSource = properties.initializeDataSourceBuilder()
        .type(HikariDataSource.class)
        .build();

    dataSource.setJdbcUrl(conn.jdbcUrl());
    if (StringUtils.hasText(conn.username())) {
      dataSource.setUsername(conn.username());
    }
    if (StringUtils.hasText(conn.password())) {
      dataSource.setPassword(conn.password());
    }
    if (conn.driverClassName() != null) {
      dataSource.setDriverClassName(conn.driverClassName());
    } else if (conn.jdbcUrl() != null && conn.jdbcUrl().startsWith("jdbc:postgresql:")) {
      dataSource.setDriverClassName("org.postgresql.Driver");
    } else if (conn.jdbcUrl() != null && conn.jdbcUrl().startsWith("jdbc:h2:")) {
      dataSource.setDriverClassName("org.h2.Driver");
    }

    log.info("Initialized primary DataSource with URL: {}", sanitizeUrl(conn.jdbcUrl()));
    return dataSource;
  }

  public static ParsedConnection parseConnection(String rawUrl, String defaultUser, String defaultPassword) {
    if (!StringUtils.hasText(rawUrl)) {
      return new ParsedConnection(rawUrl, defaultUser, defaultPassword, null);
    }
    String trimmed = rawUrl.trim();

    // Already a standard JDBC URL
    if (trimmed.startsWith("jdbc:")) {
      String driver = null;
      if (trimmed.startsWith("jdbc:postgresql:")) {
        driver = "org.postgresql.Driver";
      } else if (trimmed.startsWith("jdbc:h2:")) {
        driver = "org.h2.Driver";
      }
      return new ParsedConnection(trimmed, defaultUser, defaultPassword, driver);
    }

    // Convert postgres:// or postgresql:// to standard JDBC URL
    if (trimmed.startsWith("postgres://") || trimmed.startsWith("postgresql://")) {
      try {
        String uriStr = trimmed.startsWith("postgres://")
            ? "http://" + trimmed.substring("postgres://".length())
            : "http://" + trimmed.substring("postgresql://".length());

        URI uri = new URI(uriStr);
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String path = uri.getPath();
        if (path != null && path.startsWith("/")) {
          path = path.substring(1);
        }

        String username = defaultUser;
        String password = defaultPassword;
        if (uri.getUserInfo() != null) {
          String[] parts = uri.getUserInfo().split(":", 2);
          if (parts.length > 0 && StringUtils.hasText(parts[0])) {
            username = parts[0];
          }
          if (parts.length > 1) {
            password = parts[1];
          }
        }

        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + (path != null ? path : "");
        String query = uri.getQuery();
        if (StringUtils.hasText(query)) {
          jdbcUrl += "?" + query;
        }

        return new ParsedConnection(jdbcUrl, username, password, "org.postgresql.Driver");
      } catch (Exception e) {
        String fallback = trimmed.startsWith("postgresql://")
            ? "jdbc:" + trimmed
            : "jdbc:postgresql://" + trimmed.substring("postgres://".length());
        return new ParsedConnection(fallback, defaultUser, defaultPassword, "org.postgresql.Driver");
      }
    }

    return new ParsedConnection(trimmed, defaultUser, defaultPassword, null);
  }

  private static String sanitizeUrl(String url) {
    if (url == null) return "null";
    return url.replaceAll(":[^/@]+@", ":****@");
  }

  public record ParsedConnection(String jdbcUrl, String username, String password, String driverClassName) {}
}
