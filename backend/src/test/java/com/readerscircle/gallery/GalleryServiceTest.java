package com.readerscircle.gallery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.Membership;
import com.readerscircle.circles.MembershipRepository;
import com.readerscircle.events.Event;
import com.readerscircle.events.EventRegistration;
import com.readerscircle.events.EventRegistrationRepository;
import com.readerscircle.events.EventRepository;
import com.readerscircle.events.EventStatus;
import com.readerscircle.events.RegistrationStatus;
import com.readerscircle.gallery.dto.AddGalleryMediaRequest;
import com.readerscircle.gallery.dto.GalleryItemDto;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class GalleryServiceTest {

  private GalleryItemRepository galleryRepository;
  private EventRepository eventRepository;
  private CircleRepository circleRepository;
  private MembershipRepository membershipRepository;
  private UserRepository userRepository;
  private FileStorageService fileStorageService;
  private EventRegistrationRepository eventRegistrationRepository;
  private GalleryService galleryService;

  @BeforeEach
  void setUp() {
    galleryRepository = mock(GalleryItemRepository.class);
    eventRepository = mock(EventRepository.class);
    circleRepository = mock(CircleRepository.class);
    membershipRepository = mock(MembershipRepository.class);
    userRepository = mock(UserRepository.class);
    fileStorageService = mock(FileStorageService.class);
    eventRegistrationRepository = mock(EventRegistrationRepository.class);

    galleryService =
        new GalleryService(
            galleryRepository,
            eventRepository,
            circleRepository,
            membershipRepository,
            userRepository,
            fileStorageService,
            eventRegistrationRepository);
  }

  @Test
  void registeredReaderCanListGallery() {
    UUID circleId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID readerId = UUID.randomUUID();

    Event event = new Event(circleId, "Past Event");
    event.setStatus(EventStatus.COMPLETED);

    Circle circle = new Circle("Circle A", "City", "Desc", UUID.randomUUID());
    EventRegistration registration = new EventRegistration(eventId, readerId);

    GalleryItem item =
        new GalleryItem(eventId, UUID.randomUUID(), MediaType.PHOTO, "https://example.com/p.jpg", "Photo 1");

    when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
    when(circleRepository.findById(circleId)).thenReturn(Optional.of(circle));
    when(eventRegistrationRepository.findByEventIdAndReaderId(eventId, readerId))
        .thenReturn(Optional.of(registration));
    when(galleryRepository.findByEventIdOrderByUploadedAtAsc(eventId))
        .thenReturn(List.of(item));

    List<GalleryItemDto> result = galleryService.listGallery(eventId, readerId, Role.READER);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).mediaUrl()).isEqualTo("https://example.com/p.jpg");
  }

  @Test
  void unregisteredCircleMemberIsDenied() {
    UUID circleId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID readerId = UUID.randomUUID();

    Event event = new Event(circleId, "Past Event");
    event.setStatus(EventStatus.COMPLETED);

    Circle circle = new Circle("Circle A", "City", "Desc", UUID.randomUUID());

    Membership membership = new Membership(circleId, readerId);
    membership.approve();

    when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
    when(circleRepository.findById(circleId)).thenReturn(Optional.of(circle));
    // Reader belongs to circle, but did NOT register for this event
    when(membershipRepository.findByCircleIdAndReaderId(circleId, readerId))
        .thenReturn(Optional.of(membership));
    when(eventRegistrationRepository.findByEventIdAndReaderId(eventId, readerId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> galleryService.listGallery(eventId, readerId, Role.READER))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void readerRegisteredForDifferentEventIsDenied() {
    UUID circleId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID readerId = UUID.randomUUID();

    Event event = new Event(circleId, "Past Event");
    event.setStatus(EventStatus.COMPLETED);

    Circle circle = new Circle("Circle A", "City", "Desc", UUID.randomUUID());

    when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
    when(circleRepository.findById(circleId)).thenReturn(Optional.of(circle));
    // Reader registered for some other event, not eventId
    when(eventRegistrationRepository.findByEventIdAndReaderId(eventId, readerId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> galleryService.listGallery(eventId, readerId, Role.READER))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void circleOrganizerCanListAndUpload() {
    UUID circleId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID organizerId = UUID.randomUUID();

    Event event = new Event(circleId, "Past Event");
    event.setStatus(EventStatus.COMPLETED);

    Circle circle = new Circle("Circle A", "City", "Desc", organizerId);
    User orgUser = new User("Organizer", "org@test.com", "pass", Role.ORGANIZER);

    when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
    when(circleRepository.findById(circleId)).thenReturn(Optional.of(circle));
    when(userRepository.findById(organizerId)).thenReturn(Optional.of(orgUser));
    when(galleryRepository.findByEventIdOrderByUploadedAtAsc(eventId)).thenReturn(Collections.emptyList());
    when(galleryRepository.save(org.mockito.ArgumentMatchers.any(GalleryItem.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // Organizer can list gallery
    List<GalleryItemDto> listResult = galleryService.listGallery(eventId, organizerId, Role.ORGANIZER);
    assertThat(listResult).isNotNull();

    // Organizer can add media
    AddGalleryMediaRequest req =
        new AddGalleryMediaRequest(MediaType.PHOTO, "https://example.com/org-pic.jpg", "Snap");
    GalleryItemDto uploadResult = galleryService.addMedia(eventId, organizerId, Role.ORGANIZER, req);
    assertThat(uploadResult).isNotNull();
    assertThat(uploadResult.mediaUrl()).isEqualTo("https://example.com/org-pic.jpg");
  }

  @Test
  void adminCanListAndUpload() {
    UUID circleId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    Event event = new Event(circleId, "Past Event");
    event.setStatus(EventStatus.COMPLETED);

    Circle circle = new Circle("Circle A", "City", "Desc", UUID.randomUUID());

    when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
    when(circleRepository.findById(circleId)).thenReturn(Optional.of(circle));
    when(galleryRepository.findByEventIdOrderByUploadedAtAsc(eventId)).thenReturn(Collections.emptyList());
    when(galleryRepository.save(org.mockito.ArgumentMatchers.any(GalleryItem.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // Admin can list gallery
    List<GalleryItemDto> listResult = galleryService.listGallery(eventId, adminId, Role.ADMIN);
    assertThat(listResult).isNotNull();

    // Admin can add media
    AddGalleryMediaRequest req =
        new AddGalleryMediaRequest(MediaType.PHOTO, "https://example.com/admin-pic.jpg", "Admin snap");
    GalleryItemDto uploadResult = galleryService.addMedia(eventId, adminId, Role.ADMIN, req);
    assertThat(uploadResult).isNotNull();
    assertThat(uploadResult.mediaUrl()).isEqualTo("https://example.com/admin-pic.jpg");
  }

  @Test
  void uploadToNonCompletedEventIsRejectedWith422() {
    UUID circleId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID organizerId = UUID.randomUUID();

    Event event = new Event(circleId, "Upcoming Event");
    event.setStatus(EventStatus.PUBLISHED);

    when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

    AddGalleryMediaRequest req =
        new AddGalleryMediaRequest(MediaType.PHOTO, "https://example.com/pic.jpg", "Too early");

    assertThatThrownBy(() -> galleryService.addMedia(eventId, organizerId, Role.ORGANIZER, req))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }
}
