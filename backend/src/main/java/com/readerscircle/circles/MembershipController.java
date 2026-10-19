package com.readerscircle.circles;

import com.readerscircle.auth.Role;
import com.readerscircle.circles.dto.CircleSummaryDto;
import com.readerscircle.circles.dto.JoinActionRequest;
import com.readerscircle.circles.dto.MemberDto;
import com.readerscircle.circles.dto.MembershipDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MembershipController {

  private final MembershipService membershipService;
  private final CircleService circleService;

  public MembershipController(MembershipService membershipService, CircleService circleService) {
    this.membershipService = membershipService;
    this.circleService = circleService;
  }

  @PostMapping("/api/circles/{id}/join-requests")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.CREATED)
  public MembershipDto requestToJoin(
      @PathVariable UUID id,
      Authentication authentication) {
    UUID readerId = getUserId(authentication);
    return membershipService.requestToJoin(id, readerId);
  }

  @GetMapping("/api/circles/{id}/join-requests")
  @PreAuthorize("isAuthenticated()")
  public List<MemberDto> getPendingRequests(
      @PathVariable UUID id,
      @RequestParam(name = "search", required = false) String search,
      @RequestParam(name = "sort", required = false) String sort,
      Authentication authentication) {
    UUID actorId = getUserId(authentication);
    Role role = getUserRole(authentication);
    List<MemberDto> list = membershipService.getPendingRequests(id, actorId, role);
    return filterSortMembers(list, search, null, sort);
  }

  @GetMapping("/api/circles/{id}/members")
  @PreAuthorize("isAuthenticated()")
  public List<MemberDto> getCircleMembers(
      @PathVariable UUID id,
      @RequestParam(name = "search", required = false) String search,
      @RequestParam(name = "role", required = false) String roleFilter,
      @RequestParam(name = "sort", required = false) String sort,
      Authentication authentication) {
    UUID actorId = getUserId(authentication);
    Role role = getUserRole(authentication);
    List<MemberDto> list = membershipService.getCircleMembers(id, actorId, role);
    return filterSortMembers(list, search, roleFilter, sort);
  }

  @PatchMapping("/api/join-requests/{id}")
  @PreAuthorize("isAuthenticated()")
  public MemberDto decideJoinRequest(
      @PathVariable UUID id,
      @Valid @RequestBody JoinActionRequest req,
      Authentication authentication) {
    UUID actorId = getUserId(authentication);
    Role role = getUserRole(authentication);
    return membershipService.decideJoinRequest(id, req.action(), actorId, role);
  }

  @GetMapping("/api/users/me/memberships")
  @PreAuthorize("isAuthenticated()")
  public List<MembershipDto> getMyMemberships(Authentication authentication) {
    UUID readerId = getUserId(authentication);
    return membershipService.getMyMemberships(readerId);
  }

  /** Withdraw your own pending join request ("Cancel Request"). */
  @DeleteMapping("/api/join-requests/{id}")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancelJoinRequest(
      @PathVariable UUID id, Authentication authentication) {
    membershipService.cancelJoinRequest(id, getUserId(authentication), getUserRole(authentication));
  }

  /** Remove an approved member ("Remove" on the Members screen). Organizer of this circle or admin. */
  @DeleteMapping("/api/circles/{id}/members/{userId}")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeMember(
      @PathVariable UUID id,
      @PathVariable UUID userId,
      Authentication authentication) {
    membershipService.removeMember(id, userId, getUserId(authentication), getUserRole(authentication));
  }

  /** Member-visible stats for the Members screen header (counts only). */
  @GetMapping("/api/circles/{id}/member-stats")
  @PreAuthorize("isAuthenticated()")
  public Map<String, Long> getMemberStats(
      @PathVariable UUID id, Authentication authentication) {
    return membershipService.getMemberStats(id, getUserId(authentication), getUserRole(authentication));
  }

  @GetMapping("/api/users/me/organized-circles")
  @PreAuthorize("isAuthenticated()")
  public List<CircleSummaryDto> getMyOrganizedCircles(Authentication authentication) {
    UUID organizerId = getUserId(authentication);
    return circleService.getCirclesByOrganizer(organizerId);
  }

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
  }

  /** Search across name/email, role filter, sort newest/oldest/name for admin tables. */
  private List<MemberDto> filterSortMembers(
      List<MemberDto> list, String search, String roleFilter, String sort) {
    java.util.stream.Stream<MemberDto> s = list.stream();
    if (search != null && !search.isBlank()) {
      String q = search.trim().toLowerCase();
      s = s.filter(
          m ->
              (m.readerName() != null && m.readerName().toLowerCase().contains(q))
                  || (m.readerEmail() != null && m.readerEmail().toLowerCase().contains(q)));
    }
    if (roleFilter != null && !roleFilter.isBlank() && !"all".equalsIgnoreCase(roleFilter.trim())) {
      String rf = roleFilter.trim().toUpperCase();
      s = s.filter(m -> m.readerRole() != null && m.readerRole().name().equalsIgnoreCase(rf));
    }
    List<MemberDto> out = new java.util.ArrayList<>(s.toList());
    String so = sort == null ? "" : sort.trim().toLowerCase();
    switch (so) {
      case "oldest" -> out.sort(
          java.util.Comparator.comparing(MemberDto::requestedAt,
              java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
      case "name", "name_asc", "name-asc" -> out.sort(
          java.util.Comparator.comparing(m -> m.readerName() == null ? "" : m.readerName().toLowerCase()));
      default -> out.sort(
          java.util.Comparator.comparing(MemberDto::requestedAt,
              java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
    }
    return out;
  }

  private Role getUserRole(Authentication auth) {
    if (auth == null) return Role.READER;
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
      return Role.ADMIN;
    }
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"))) {
      return Role.ORGANIZER;
    }
    return Role.READER;
  }
}
