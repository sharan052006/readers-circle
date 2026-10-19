package com.readerscircle.circles;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.dto.MemberDto;
import com.readerscircle.circles.dto.MembershipDto;
import java.util.List;
import java.util.Map;
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

    boolean readerBlocked =
        userRepository.findById(readerId).map(u -> !u.isActive()).orElse(false);
    if (readerBlocked) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Your account has been blocked by the administrator. You cannot join circles.");
    }

    if (circle.isHidden()) {
      // Hidden channels (Organisers Lounge) are invite-only: readers cannot request in.
      Role requesterRole =
          userRepository.findById(readerId).map(User::getRole).orElse(Role.READER);
      if (requesterRole == Role.READER) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This circle is not open for join requests");
      }
    }

    if (isEffectiveOrganizer(circle, readerId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "The circle organizer cannot join their own circle as a member");
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

    boolean isOrganizer = isEffectiveOrganizer(circle, actorId);
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

    boolean isOrganizer = isEffectiveOrganizer(circle, actorId);
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
        .filter(m -> !m.getReaderId().equals(circle.getOrganizerId()))
        .map(m -> toMemberDto(m, canViewEmails))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<MemberDto> getPendingRequests(UUID circleId, UUID actorId, Role actorRole) {
    Circle circle =
        circleRepository
            .findById(circleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    boolean isOrganizer = isEffectiveOrganizer(circle, actorId);
    boolean isAdmin = actorRole == Role.ADMIN;
    if (!isOrganizer && !isAdmin) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the circle organizer or admin can view pending join requests");
    }

    return membershipRepository
        .findByCircleIdAndStatus(circleId, MembershipStatus.PENDING)
        .stream()
        .filter(m -> !m.getReaderId().equals(circle.getOrganizerId()))
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
        .filter(dto -> {
          Circle circle = circleRepository.findById(dto.circleId()).orElse(null);
          return circle == null || !isEffectiveOrganizer(circle, readerId);
        })
        .toList();
  }

  /**
   * Withdraw your own pending join request ("Cancel Request"). Approved
   * memberships cannot be cancelled here — contact the organizer.
   */
  @Transactional
  public void cancelJoinRequest(UUID membershipId, UUID readerId, Role readerRole) {
    Membership membership =
        membershipRepository
            .findById(membershipId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Join request not found"));
    if (readerRole != Role.ADMIN && !membership.getReaderId().equals(readerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your join request");
    }
    if (membership.getStatus() != MembershipStatus.PENDING) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only pending requests can be cancelled");
    }
    membershipRepository.delete(membership);
  }

  /**
   * Remove a member from the circle ("Remove" on the Members screen).
   * Only the circle organizer or an admin. The organizer themself can never
   * be removed this way. Pending requests go through REJECT instead.
   */
  @Transactional
  public void removeMember(UUID circleId, UUID targetUserId, UUID actorId, Role actorRole) {
    Circle circle =
        circleRepository
            .findById(circleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));
    boolean isOrganizer = isEffectiveOrganizer(circle, actorId);
    boolean isAdmin = actorRole == Role.ADMIN;
    if (!isOrganizer && !isAdmin) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the circle organizer or admin can remove members");
    }
    if (isEffectiveOrganizer(circle, targetUserId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "The organizer cannot be removed from their own circle");
    }
    Membership membership =
        membershipRepository
            .findByCircleIdAndReaderId(circleId, targetUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
    if (membership.getStatus() != MembershipStatus.APPROVED) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only approved members can be removed (reject pending requests instead)");
    }
    membershipRepository.delete(membership);
  }

  /**
   * Member-visible stats for the Members screen header (counts only — the
   * pending identity list stays organizer/admin-only).
   */
  @Transactional(readOnly = true)
  public Map<String, Long> getMemberStats(UUID circleId, UUID actorId, Role actorRole) {
    Circle circle =
        circleRepository
            .findById(circleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));
    boolean isOrganizer = isEffectiveOrganizer(circle, actorId);
    boolean isAdmin = actorRole == Role.ADMIN;
    boolean isMember =
        membershipRepository.existsByCircleIdAndReaderIdAndStatus(circleId, actorId, MembershipStatus.APPROVED);
    if (!isOrganizer && !isAdmin && !isMember) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Must be an approved member, circle organizer, or admin to view members");
    }
    long approved =
        membershipRepository.findByCircleIdAndStatus(circleId, MembershipStatus.APPROVED).stream()
            .filter(m -> !m.getReaderId().equals(circle.getOrganizerId()))
            .count();
    long pending = membershipRepository.countByCircleIdAndStatus(circleId, MembershipStatus.PENDING);
    Map<String, Long> out = new java.util.LinkedHashMap<>();
    out.put("totalMembers", approved + 1);
    out.put("regularMembers", approved);
    out.put("pendingRequests", pending);
    return out;
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
        .map(c -> isEffectiveOrganizer(c, userId))
        .orElse(false);
  }

  /**
   * Effective organizer = assigned as the circle's organizer AND still holding
   * a live ORGANIZER/ADMIN role. Role is read from the DB (not the JWT) so a
   * demotion takes effect immediately, even for tokens minted before it.
   */
  private boolean isEffectiveOrganizer(Circle circle, UUID userId) {
    if (circle == null || userId == null || !userId.equals(circle.getOrganizerId())) {
      return false;
    }
    Role role = userRepository.findById(userId).map(User::getRole).orElse(null);
    return role == Role.ORGANIZER || role == Role.ADMIN;
  }

  private MembershipDto toMembershipDto(Membership m, Circle circle) {
    String circleName = circle != null ? circle.getName() : "Unknown";
    String circleCity = circle != null ? circle.getCity() : "Unknown";
    String cover = circle != null ? circle.getCoverImageUrl() : null;
    long members = 0;
    if (circle != null) {
      long approved =
          membershipRepository.countByCircleIdAndStatusAndReaderIdNot(
              circle.getId(), MembershipStatus.APPROVED, circle.getOrganizerId());
      members = approved + 1;
    }
    return new MembershipDto(
        m.getId(),
        m.getCircleId(),
        circleName,
        circleCity,
        m.getReaderId(),
        m.getStatus(),
        m.getRequestedAt(),
        m.getDecidedAt(),
        cover,
        members);
  }

  private MemberDto toMemberDto(Membership m, boolean includeEmail) {
    User user = userRepository.findById(m.getReaderId()).orElse(null);
    String readerName = user != null ? user.getName() : "Unknown";
    String readerEmail = (user != null && includeEmail) ? user.getEmail() : null;
    Role readerRole = user != null ? user.getRole() : null;
    String bio = user != null ? user.getBio() : null;
    String avatar = user != null ? user.getProfilePictureUrl() : null;
    String city = user != null ? user.getCity() : null;
    java.util.List<String> interests =
        user != null ? splitInterests(user.getInterests()) : java.util.List.of();
    return new MemberDto(
        m.getId(),
        m.getReaderId(),
        readerName,
        readerEmail,
        readerRole,
        m.getStatus(),
        m.getRequestedAt(),
        m.getDecidedAt(),
        bio,
        avatar,
        city,
        interests);
  }

  private static java.util.List<String> splitInterests(String interests) {
    if (interests == null || interests.isBlank()) {
      return java.util.List.of();
    }
    java.util.List<String> out = new java.util.ArrayList<>();
    for (String part : interests.split(",")) {
      if (!part.isBlank()) {
        out.add(part.trim());
      }
    }
    return out;
  }
}
