package com.readerscircle.circles;

import com.readerscircle.auth.Role;
import com.readerscircle.circles.dto.JoinActionRequest;
import com.readerscircle.circles.dto.MemberDto;
import com.readerscircle.circles.dto.MembershipDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MembershipController {

  private final MembershipService membershipService;

  public MembershipController(MembershipService membershipService) {
    this.membershipService = membershipService;
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
      Authentication authentication) {
    UUID actorId = getUserId(authentication);
    Role role = getUserRole(authentication);
    return membershipService.getPendingRequests(id, actorId, role);
  }

  @GetMapping("/api/circles/{id}/members")
  @PreAuthorize("isAuthenticated()")
  public List<MemberDto> getCircleMembers(
      @PathVariable UUID id,
      Authentication authentication) {
    UUID actorId = getUserId(authentication);
    Role role = getUserRole(authentication);
    return membershipService.getCircleMembers(id, actorId, role);
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

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
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
