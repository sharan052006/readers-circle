package com.readerscircle.auth;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  List<User> findByActiveTrueAndRoleInOrderByNameAsc(Collection<Role> roles);

  long countByRole(Role role);

  long countByRoleIn(Collection<Role> roles);

  long countByCreatedAtAfter(java.time.OffsetDateTime after);

  long countByRoleInAndCreatedAtAfter(Collection<Role> roles, java.time.OffsetDateTime after);

  long countByActiveTrue();

  List<User> findAllByOrderByCreatedAtAsc();

  /**
   * Admin user-list search backing the User Management screens (search box +
   * role/status filters). All filter params are optional (null = no filter).
   */
  @Query(
      "SELECT u FROM User u WHERE (:role IS NULL OR u.role = :role)"
          + " AND (:active IS NULL OR u.active = :active)"
          + " AND (:q IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))"
          + " OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))")
  Page<User> search(
      @Param("role") Role role,
      @Param("active") Boolean active,
      @Param("q") String q,
      Pageable pageable);
}
