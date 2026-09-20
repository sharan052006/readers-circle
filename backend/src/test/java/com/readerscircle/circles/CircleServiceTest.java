package com.readerscircle.circles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.dto.CircleDetailDto;
import com.readerscircle.circles.dto.CircleSummaryDto;
import com.readerscircle.circles.dto.CreateCircleRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CircleServiceTest {

  private CircleRepository circleRepository;
  private MembershipRepository membershipRepository;
  private UserRepository userRepository;
  private CircleService circleService;

  @BeforeEach
  void setUp() {
    circleRepository = mock(CircleRepository.class);
    membershipRepository = mock(MembershipRepository.class);
    userRepository = mock(UserRepository.class);
    circleService = new CircleService(circleRepository, membershipRepository, userRepository);
  }

  @Test
  void listCirclesWithCityFiltersActiveOnlyForNonAdmin() {
    Circle c1 = new Circle("Dhaka Readers", "Dhaka", "Description", UUID.randomUUID());
    when(circleRepository.findByCityIgnoreCaseAndStatus("dhaka", CircleStatus.ACTIVE))
        .thenReturn(List.of(c1));

    List<CircleSummaryDto> result = circleService.listCircles("dhaka", false);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("Dhaka Readers");
  }

  @Test
  void getCircleThrowsNotFoundWhenInactiveAndNotAdmin() {
    UUID circleId = UUID.randomUUID();
    Circle c = new Circle("Quiet Club", "Chittagong", "Desc", UUID.randomUUID());
    c.setStatus(CircleStatus.INACTIVE);
    when(circleRepository.findById(circleId)).thenReturn(Optional.of(c));

    assertThatThrownBy(() -> circleService.getCircle(circleId, false))
        .isInstanceOf(ResponseStatusException.class)
        .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.NOT_FOUND);
  }

  @Test
  void getCircleReturnsInactiveWhenAdmin() {
    UUID circleId = UUID.randomUUID();
    UUID organizerId = UUID.randomUUID();
    Circle c = new Circle("Quiet Club", "Chittagong", "Desc", organizerId);
    c.setStatus(CircleStatus.INACTIVE);
    when(circleRepository.findById(circleId)).thenReturn(Optional.of(c));
    when(userRepository.findById(organizerId)).thenReturn(Optional.of(new User("Org", "org@test.com", "hash", Role.ORGANIZER)));

    CircleDetailDto dto = circleService.getCircle(circleId, true);

    assertThat(dto.status()).isEqualTo(CircleStatus.INACTIVE);
    assertThat(dto.name()).isEqualTo("Quiet Club");
  }

  @Test
  void createCircleRegistersOrganizerAsApprovedMember() {
    UUID organizerId = UUID.randomUUID();
    User organizer = new User("Jane Org", "jane@test.com", "hash", Role.ORGANIZER);
    when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

    when(circleRepository.save(any(Circle.class))).thenAnswer(inv -> inv.getArgument(0));
    when(membershipRepository.findByCircleIdAndReaderId(any(), any())).thenReturn(Optional.empty());

    CreateCircleRequest req = new CreateCircleRequest("Mystery Club", "Sylhet", "Mystery books", organizerId);
    CircleDetailDto created = circleService.createCircle(req);

    assertThat(created.name()).isEqualTo("Mystery Club");
    verify(membershipRepository).save(any(Membership.class));
  }
}
