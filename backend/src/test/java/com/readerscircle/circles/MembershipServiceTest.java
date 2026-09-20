package com.readerscircle.circles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.dto.MemberDto;
import com.readerscircle.circles.dto.MembershipDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class MembershipServiceTest {

  private MembershipRepository membershipRepository;
  private CircleRepository circleRepository;
  private UserRepository userRepository;
  private MembershipService membershipService;

  @BeforeEach
  void setUp() {
    membershipRepository = mock(MembershipRepository.class);
    circleRepository = mock(CircleRepository.class);
    userRepository = mock(UserRepository.class);
    membershipService = new MembershipService(membershipRepository, circleRepository, userRepository);
  }

  @Test
  void requestToJoinInactiveCircleThrowsBadRequest() {
    UUID circleId = UUID.randomUUID();
    Circle c = new Circle("Inactive Club", "Khulna", "Desc", UUID.randomUUID());
    c.setStatus(CircleStatus.INACTIVE);
    when(circleRepository.findById(circleId)).thenReturn(Optional.of(c));

    assertThatThrownBy(() -> membershipService.requestToJoin(circleId, UUID.randomUUID()))
        .isInstanceOf(ResponseStatusException.class)
        .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.BAD_REQUEST);
  }

  @Test
  void requestToJoinSavesNewPendingMembership() {
    UUID circleId = UUID.randomUUID();
    UUID readerId = UUID.randomUUID();
    Circle c = new Circle("Classics", "Rajshahi", "Desc", UUID.randomUUID());
    when(circleRepository.findById(circleId)).thenReturn(Optional.of(c));
    when(membershipRepository.findByCircleIdAndReaderId(circleId, readerId)).thenReturn(Optional.empty());
    when(membershipRepository.saveAndFlush(any(Membership.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    MembershipDto dto = membershipService.requestToJoin(circleId, readerId);

    assertThat(dto.status()).isEqualTo(MembershipStatus.PENDING);
  }

  @Test
  void decideJoinRequestSucceedsForCircleOrganizer() {
    UUID circleId = UUID.randomUUID();
    UUID organizerId = UUID.randomUUID();
    UUID membershipId = UUID.randomUUID();

    Circle c = new Circle("SciFi", "Dhaka", "Desc", organizerId);
    Membership m = new Membership(circleId, UUID.randomUUID());

    when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(m));
    when(circleRepository.findById(circleId)).thenReturn(Optional.of(c));
    when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

    MemberDto res = membershipService.decideJoinRequest(membershipId, "APPROVE", organizerId, Role.ORGANIZER);

    assertThat(res.status()).isEqualTo(MembershipStatus.APPROVED);
    assertThat(m.getStatus()).isEqualTo(MembershipStatus.APPROVED);
  }

  @Test
  void decideJoinRequestRejectsCrossCircleOrganizerWithForbidden() {
    UUID circleId = UUID.randomUUID();
    UUID actualOrganizerId = UUID.randomUUID();
    UUID foreignOrganizerId = UUID.randomUUID();
    UUID membershipId = UUID.randomUUID();

    Circle c = new Circle("SciFi", "Dhaka", "Desc", actualOrganizerId);
    Membership m = new Membership(circleId, UUID.randomUUID());

    when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(m));
    when(circleRepository.findById(circleId)).thenReturn(Optional.of(c));

    assertThatThrownBy(
            () ->
                membershipService.decideJoinRequest(
                    membershipId, "APPROVE", foreignOrganizerId, Role.ORGANIZER))
        .isInstanceOf(ResponseStatusException.class)
        .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.FORBIDDEN);
  }

  @Test
  void getCircleMembersHidesEmailsFromFellowReaders() {
    UUID circleId = UUID.randomUUID();
    UUID memberReaderId = UUID.randomUUID();
    UUID callerReaderId = UUID.randomUUID();

    Circle c = new Circle("Poetry", "Dhaka", "Desc", UUID.randomUUID());
    Membership m = new Membership(circleId, memberReaderId);
    m.approve();

    when(circleRepository.findById(circleId)).thenReturn(Optional.of(c));
    when(membershipRepository.existsByCircleIdAndReaderIdAndStatus(
            circleId, callerReaderId, MembershipStatus.APPROVED))
        .thenReturn(true);
    when(membershipRepository.findByCircleIdAndStatus(circleId, MembershipStatus.APPROVED))
        .thenReturn(List.of(m));
    when(userRepository.findById(memberReaderId))
        .thenReturn(Optional.of(new User("Reader Bob", "bob@secret.com", "hash", Role.READER)));

    List<MemberDto> members = membershipService.getCircleMembers(circleId, callerReaderId, Role.READER);

    assertThat(members).hasSize(1);
    assertThat(members.get(0).readerName()).isEqualTo("Reader Bob");
    assertThat(members.get(0).readerEmail()).isNull(); // Privacy preserved
  }
}
