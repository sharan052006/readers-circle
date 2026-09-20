package com.readerscircle.circles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class MembershipTransitionTest {

  @Test
  void initialMembershipIsPending() {
    UUID circleId = UUID.randomUUID();
    UUID readerId = UUID.randomUUID();
    Membership m = new Membership(circleId, readerId);

    assertThat(m.getStatus()).isEqualTo(MembershipStatus.PENDING);
    assertThat(m.getRequestedAt()).isNotNull();
    assertThat(m.getDecidedAt()).isNull();
  }

  @Test
  void approveStampsDecidedAt() {
    Membership m = new Membership(UUID.randomUUID(), UUID.randomUUID());
    m.approve();

    assertThat(m.getStatus()).isEqualTo(MembershipStatus.APPROVED);
    assertThat(m.getDecidedAt()).isNotNull();
  }

  @Test
  void rejectStampsDecidedAt() {
    Membership m = new Membership(UUID.randomUUID(), UUID.randomUUID());
    m.reject();

    assertThat(m.getStatus()).isEqualTo(MembershipStatus.REJECTED);
    assertThat(m.getDecidedAt()).isNotNull();
  }

  @Test
  void resubmitOnRejectedFlipsToPending() {
    Membership m = new Membership(UUID.randomUUID(), UUID.randomUUID());
    m.reject();
    assertThat(m.getDecidedAt()).isNotNull();

    m.resubmit();
    assertThat(m.getStatus()).isEqualTo(MembershipStatus.PENDING);
    assertThat(m.getDecidedAt()).isNull();
  }

  @Test
  void resubmitOnPendingThrowsConflict() {
    Membership m = new Membership(UUID.randomUUID(), UUID.randomUUID());

    assertThatThrownBy(m::resubmit)
        .isInstanceOf(ResponseStatusException.class)
        .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.CONFLICT);
  }

  @Test
  void resubmitOnApprovedThrowsConflict() {
    Membership m = new Membership(UUID.randomUUID(), UUID.randomUUID());
    m.approve();

    assertThatThrownBy(m::resubmit)
        .isInstanceOf(ResponseStatusException.class)
        .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.CONFLICT);
  }
}
