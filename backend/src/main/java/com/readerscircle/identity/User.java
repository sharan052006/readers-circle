package com.readerscircle.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 320, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role = Role.READER;

  @Column(nullable = false)
  private boolean deactivated = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  protected User() {}

  public User(String name, String email, String passwordHash, Role role) {
    this.name = name;
    this.email = normalizeEmail(email);
    this.passwordHash = passwordHash;
    this.role = role;
  }

  public static String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public boolean isDeactivated() {
    return deactivated;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public void setDeactivated(boolean deactivated) {
    this.deactivated = deactivated;
  }
}
