package com.readerscircle.circles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

  Optional<Membership> findByCircleIdAndReaderId(UUID circleId, UUID readerId);

  boolean existsByCircleIdAndReaderIdAndStatus(UUID circleId, UUID readerId, MembershipStatus status);

  List<Membership> findByCircleIdAndStatus(UUID circleId, MembershipStatus status);

  List<Membership> findByReaderId(UUID readerId);

  long countByCircleIdAndStatus(UUID circleId, MembershipStatus status);

  long countByCircleIdAndStatusAndReaderIdNot(UUID circleId, MembershipStatus status, UUID readerId);

  long countByStatus(MembershipStatus status);

  long countByStatusAndRequestedAtAfter(MembershipStatus status, java.time.OffsetDateTime after);

  long countByCircleIdAndStatusAndDecidedAtAfter(
      UUID circleId, MembershipStatus status, java.time.OffsetDateTime after);

  List<Membership> findTop10ByStatusOrderByDecidedAtDesc(MembershipStatus status);
}
