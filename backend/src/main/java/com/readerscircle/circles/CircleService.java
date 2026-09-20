package com.readerscircle.circles;

import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.dto.CircleDetailDto;
import com.readerscircle.circles.dto.CircleSummaryDto;
import com.readerscircle.circles.dto.CreateCircleRequest;
import com.readerscircle.circles.dto.UpdateCircleRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CircleService {

  private final CircleRepository circleRepository;
  private final MembershipRepository membershipRepository;
  private final UserRepository userRepository;

  public CircleService(
      CircleRepository circleRepository,
      MembershipRepository membershipRepository,
      UserRepository userRepository) {
    this.circleRepository = circleRepository;
    this.membershipRepository = membershipRepository;
    this.userRepository = userRepository;
  }

  @Transactional(readOnly = true)
  public List<CircleSummaryDto> listCircles(String city, boolean isAdmin) {
    List<Circle> circles;
    if (city != null && !city.isBlank()) {
      if (isAdmin) {
        circles =
            circleRepository.findAll().stream()
                .filter(c -> c.getCity().equalsIgnoreCase(city.trim()))
                .toList();
      } else {
        circles = circleRepository.findByCityIgnoreCaseAndStatus(city.trim(), CircleStatus.ACTIVE);
      }
    } else {
      circles = isAdmin ? circleRepository.findAll() : circleRepository.findAllByStatus(CircleStatus.ACTIVE);
    }

    return circles.stream().map(this::toSummaryDto).toList();
  }

  @Transactional(readOnly = true)
  public CircleDetailDto getCircle(UUID id, boolean isAdmin) {
    Circle circle =
        circleRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    if (!circle.isActive() && !isAdmin) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found");
    }

    return toDetailDto(circle);
  }

  @Transactional
  public CircleDetailDto createCircle(CreateCircleRequest req) {
    User organizer =
        userRepository
            .findById(req.organizerId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organizer user not found"));

    Circle circle =
        new Circle(req.name().trim(), req.city().trim(), req.description().trim(), organizer.getId());
    Circle saved = circleRepository.save(circle);

    // Automatically make the organizer an APPROVED member
    membershipRepository
        .findByCircleIdAndReaderId(saved.getId(), organizer.getId())
        .orElseGet(() -> {
          Membership m = new Membership(saved.getId(), organizer.getId());
          m.approve();
          return membershipRepository.save(m);
        });

    return toDetailDto(saved);
  }

  @Transactional
  public CircleDetailDto updateCircle(UUID id, UpdateCircleRequest req) {
    Circle circle =
        circleRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    if (req.name() != null && !req.name().isBlank()) {
      circle.setName(req.name().trim());
    }
    if (req.city() != null && !req.city().isBlank()) {
      circle.setCity(req.city().trim());
    }
    if (req.description() != null && !req.description().isBlank()) {
      circle.setDescription(req.description().trim());
    }
    if (req.organizerId() != null) {
      if (!userRepository.existsById(req.organizerId())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organizer user not found");
      }
      circle.setOrganizerId(req.organizerId());
    }
    if (req.status() != null) {
      circle.setStatus(req.status());
    }

    Circle saved = circleRepository.save(circle);
    return toDetailDto(saved);
  }

  @Transactional
  public void deactivateCircle(UUID id) {
    Circle circle =
        circleRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));
    circle.setStatus(CircleStatus.INACTIVE);
    circleRepository.save(circle);
  }

  private CircleSummaryDto toSummaryDto(Circle circle) {
    long memberCount = membershipRepository.countByCircleIdAndStatus(circle.getId(), MembershipStatus.APPROVED);
    return new CircleSummaryDto(
        circle.getId(),
        circle.getName(),
        circle.getCity(),
        circle.getDescription(),
        circle.getOrganizerId(),
        circle.getStatus(),
        circle.getCreatedAt(),
        memberCount);
  }

  private CircleDetailDto toDetailDto(Circle circle) {
    String organizerName =
        userRepository.findById(circle.getOrganizerId()).map(User::getName).orElse("Unknown");
    long memberCount = membershipRepository.countByCircleIdAndStatus(circle.getId(), MembershipStatus.APPROVED);
    return new CircleDetailDto(
        circle.getId(),
        circle.getName(),
        circle.getCity(),
        circle.getDescription(),
        circle.getOrganizerId(),
        organizerName,
        circle.getStatus(),
        circle.getCreatedAt(),
        memberCount);
  }
}
