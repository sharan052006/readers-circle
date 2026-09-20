package com.readerscircle.gallery;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.Membership;
import com.readerscircle.circles.MembershipRepository;
import com.readerscircle.circles.MembershipStatus;
import com.readerscircle.events.Event;
import com.readerscircle.events.EventRepository;
import com.readerscircle.events.EventStatus;
import com.readerscircle.gallery.dto.AddGalleryMediaRequest;
import com.readerscircle.gallery.dto.GalleryItemDto;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GalleryService {

  private final GalleryItemRepository galleryRepository;
  private final EventRepository eventRepository;
  private final CircleRepository circleRepository;
  private final MembershipRepository membershipRepository;
  private final UserRepository userRepository;

  public GalleryService(
      GalleryItemRepository galleryRepository,
      EventRepository eventRepository,
      CircleRepository circleRepository,
      MembershipRepository membershipRepository,
      UserRepository userRepository) {
    this.galleryRepository = galleryRepository;
    this.eventRepository = eventRepository;
    this.circleRepository = circleRepository;
    this.membershipRepository = membershipRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public GalleryItemDto addMedia(
      UUID eventId, UUID callerId, Role callerRole, AddGalleryMediaRequest req) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    // Gate 1: Event must be COMPLETED (FR-13)
    if (event.getStatus() != EventStatus.COMPLETED) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "Gallery uploads are only permitted for COMPLETED events. Current status: " + event.getStatus());
    }

    Circle circle =
        circleRepository
            .findById(event.getCircleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    // Gate 2: Organizer or Admin only
    assertOrganizerOrAdmin(circle, callerId, callerRole);

    // Validate media URL
    String url = req.mediaUrl() != null ? req.mediaUrl().trim() : "";
    if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("data:")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Media URL must be a valid http, https, or data URL");
    }

    GalleryItem item =
        new GalleryItem(eventId, callerId, req.mediaType(), url, req.caption());
    GalleryItem saved = galleryRepository.save(item);

    String uploaderName =
        userRepository
            .findById(callerId)
            .map(User::getName)
            .orElse("Circle Organizer");

    return toDto(saved, uploaderName);
  }

  @Transactional(readOnly = true)
  public List<GalleryItemDto> listGallery(UUID eventId, UUID callerId, Role callerRole) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    Circle circle =
        circleRepository
            .findById(event.getCircleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    // Gate: caller must be circle member (APPROVED) or organizer or Admin (FR-15 / PRD Q4)
    assertApprovedMemberOrOrganizer(circle, callerId, callerRole);

    List<GalleryItem> items =
        galleryRepository.findByEventIdOrderByUploadedAtAsc(eventId);

    return items.stream()
        .map(
            item -> {
              String name =
                  userRepository
                      .findById(item.getUploadedBy())
                      .map(User::getName)
                      .orElse("Organizer");
              return toDto(item, name);
            })
        .toList();
  }

  @Transactional
  public void deleteItem(UUID itemId, UUID callerId, Role callerRole) {
    GalleryItem item =
        galleryRepository
            .findById(itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gallery item not found"));

    Event event =
        eventRepository
            .findById(item.getEventId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    Circle circle =
        circleRepository
            .findById(event.getCircleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    // Gate: Only circle organizer or Admin (or original uploader) can delete
    assertOrganizerOrAdmin(circle, callerId, callerRole);

    galleryRepository.delete(item);
  }

  private void assertOrganizerOrAdmin(Circle circle, UUID callerId, Role callerRole) {
    if (callerRole == Role.ADMIN) {
      return;
    }
    if (callerId == null || !circle.getOrganizerId().equals(callerId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the circle organizer or admin can perform this action");
    }
  }

  private void assertApprovedMemberOrOrganizer(Circle circle, UUID callerId, Role callerRole) {
    if (callerRole == Role.ADMIN) {
      return;
    }
    if (callerId != null && circle.getOrganizerId().equals(callerId)) {
      return;
    }
    Membership membership =
        membershipRepository
            .findByCircleIdAndReaderId(circle.getId(), callerId)
            .orElse(null);

    if (membership == null || membership.getStatus() != MembershipStatus.APPROVED) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only approved members of this circle can view the event gallery");
    }
  }

  private GalleryItemDto toDto(GalleryItem item, String uploaderName) {
    return new GalleryItemDto(
        item.getId(),
        item.getEventId(),
        item.getUploadedBy(),
        uploaderName,
        item.getMediaType(),
        item.getMediaUrl(),
        item.getCaption(),
        item.getUploadedAt());
  }
}
