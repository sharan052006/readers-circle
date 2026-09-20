package com.readerscircle.circles;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.dto.MemberDto;
import com.readerscircle.circles.dto.MembershipDto;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MembershipService {

  private final MembershipRepository membershipRepository;
  private final CircleRepository circleRepository;
  private final UserRepository userRepository;

  public MembershipService(
      MembershipRepository membershipRepository,
      CircleRepository circleRepository,
      UserRepository userRepository) {
    this.membershipRepository = membershipRepository;
    this.circleRepository = circleRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public MembershipDto requestToJoin(UUID circleId, UUID readerId) {
    Circle circle =
        circleRepository
            .findById(circleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    if (!circle.isActive()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot join an inactive circle");
    }

    try {
      Membership membership =
          membershipRepository
              .findByCircleIdAndReaderId(circleId, readerId)
              .map(Membership::resubmit)
              .orElseGet(() -> new Membership(circleId, readerId));

      Membership saved = membershipRepository.saveAndFlush(membership);
      return toMembershipDto(saved, circle);
    } catch (DataIntegrityViolationException race) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Join request already pending");
    }
  }

  @Transactional
  public MemberDto decideJoinRequest(UUID membershipId, String action, UUID actorId, Role actorRole) {
    Membership membership =
        membershipRepository
            .findById(membershipId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership request not found"));

    Circle circle =
        circleRepository
            .findById(membership.getCircleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    boolean isOrganizer = circle.getOrganizerId().equals(actorId);
    boolean isAdmin = actorRole == Role.ADMIN;
    if (!isOrganizer && !isAdmin) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the circle organizer or admin can decide join requests");
    }

    if ("APPROVE".equalsIgnoreCase(action)) {
      membership.approve();
    } else if ("REJECT".equalsIgnoreCase(action)) {
      membership.reject();
    } else {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid action: must be APPROVE or REJECT");
    }

    Membership saved = membershipRepository.save(membership);
    return toMemberDto(saved, true);
  }

  @Transactional(readOnly = true)
  public List<MemberDto> getCircleMembers(UUID circleId, UUID actorId, Role actorRole) {
    Circle circle =
        circleRepository
            .findById(circleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    boolean isOrganizer = circle.getOrganizerId().equals(actorId);
    boolean isAdmin = actorRole == Role.ADMIN;
    boolean isMember =
        membershipRepository.existsByCircleIdAndReaderIdAndStatus(circleId, actorId, MembershipStatus.APPROVED);

    if (!isOrganizer && !isAdmin && !isMember) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Must be an approved member, circle organizer, or admin to view members");
    }

    boolean canViewEmails = isOrganizer || isAdmin;
    return membershipRepository
        .findByCircleIdAndStatus(circleId, MembershipStatus.APPROVED)
        .stream()
        .map(m -> toMemberDto(m, canViewEmails))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<MemberDto> getPendingRequests(UUID circleId, UUID actorId, Role actorRole) {
    Circle circle =
        circleRepository
            .findById(circleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    boolean isOrganizer = circle.getOrganizerId().equals(actorId);
    boolean isAdmin = actorRole == Role.ADMIN;
    if (!isOrganizer && !isAdmin) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the circle organizer or admin can view pending join requests");
    }

    return membershipRepository
        .findByCircleIdAndStatus(circleId, MembershipStatus.PENDING)
        .stream()
        .map(m -> toMemberDto(m, true))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<MembershipDto> getMyMemberships(UUID readerId) {
    return membershipRepository.findByReaderId(readerId).stream()
        .map(
            m -> {
              Circle circle = circleRepository.findById(m.getCircleId()).orElse(null);
              return toMembershipDto(m, circle);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public boolean isApprovedMember(UUID circleId, UUID userId) {
    return membershipRepository.existsByCircleIdAndReaderIdAndStatus(
        circleId, userId, MembershipStatus.APPROVED);
  }

  @Transactional(readOnly = true)
  public boolean isCircleOrganizer(UUID circleId, UUID userId) {
    return circleRepository
        .findById(circleId)
        .map(c -> c.getOrganizerId().equals(userId))
        .orElse(false);
  }

  private MembershipDto toMembershipDto(Membership m, Circle circle) {
    String circleName = circle != null ? circle.getName() : "Unknown";
    String circleCity = circle != null ? circle.getCity() : "Unknown";
    return new MembershipDto(
        m.getId(),
        m.getCircleId(),
        circleName,
        circleCity,
        m.getReaderId(),
        m.getStatus(),
        m.getRequestedAt(),
        m.getDecidedAt());
  }

  private MemberDto toMemberDto(Membership m, boolean includeEmail) {
    User user = userRepository.findById(m.getReaderId()).orElse(null);
    String readerName = user != null ? user.getName() : "Unknown";
    String readerEmail = (user != null && includeEmail) ? user.getEmail() : null;
    return new MemberDto(
        m.getId(),
        m.getReaderId(),
        readerName,
        readerEmail,
        m.getStatus(),
        m.getRequestedAt(),
        m.getDecidedAt());
  }
}
