package com.readerscircle.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.auth.UserService;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.Membership;
import com.readerscircle.circles.MembershipRepository;
import com.readerscircle.events.Event;
import com.readerscircle.events.EventRepository;
import com.readerscircle.events.EventStatus;
import com.readerscircle.gallery.dto.AddGalleryMediaRequest;
import com.readerscircle.gallery.dto.GalleryItemDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GalleryAccessTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("app.jwt.secret", () -> "gallery-access-test-secret-at-least-32-chars");
  }

  @Autowired TestRestTemplate rest;
  @Autowired CircleRepository circleRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired EventRepository eventRepository;
  @Autowired GalleryItemRepository galleryRepository;
  @Autowired UserRepository userRepository;
  @Autowired UserService userService;
  @Autowired com.readerscircle.auth.AuthService authService;

  private String adminToken;
  private String organizerToken;
  private String otherOrgToken;
  private String memberToken;
  private String nonMemberToken;
  private UUID circleId;
  private UUID completedEventId;
  private UUID publishedEventId;
  private UUID otherEventId;

  @BeforeEach
  void seed() {
    galleryRepository.deleteAll();
    eventRepository.deleteAll();
    membershipRepository.deleteAll();
    circleRepository.deleteAll();
    userRepository.deleteAll();

    User admin = userService.create("Admin User", "admin@gallery.test", "pass12345", Role.ADMIN);
    adminToken = authService.login("admin@gallery.test", "pass12345").accessToken();

    User organizer = userService.create("Organizer User", "org@gallery.test", "pass12345", Role.ORGANIZER);
    organizerToken = authService.login("org@gallery.test", "pass12345").accessToken();

    User otherOrg = userService.create("Other Org", "other@gallery.test", "pass12345", Role.ORGANIZER);
    otherOrgToken = authService.login("other@gallery.test", "pass12345").accessToken();

    User member = userService.create("Member User", "member@gallery.test", "pass12345", Role.READER);
    memberToken = authService.login("member@gallery.test", "pass12345").accessToken();

    User nonMember = userService.create("Non Member", "nonmember@gallery.test", "pass12345", Role.READER);
    nonMemberToken = authService.login("nonmember@gallery.test", "pass12345").accessToken();

    Circle circle = new Circle("Literary Classics", "Vienna", "Classics Discussion", organizer.getId());
    circle = circleRepository.save(circle);
    circleId = circle.getId();

    Membership membership = new Membership(circleId, member.getId());
    membership.approve();
    membershipRepository.save(membership);

    // Event 1: COMPLETED
    Event completedEvent = new Event(circleId, "Past Odyssey Book Discussion");
    completedEvent.setVenue("Cafe Central");
    completedEvent.setEventDate(LocalDate.now().minusDays(3));
    completedEvent.setEventTime(LocalTime.of(18, 0));
    completedEvent.setCapacity(20);
    completedEvent.setRegistrationDeadline(Instant.now().minusSeconds(86400 * 4));
    completedEvent.publish();
    completedEvent.complete();
    completedEvent = eventRepository.save(completedEvent);
    completedEventId = completedEvent.getId();

    // Event 2: PUBLISHED
    Event publishedEvent = new Event(circleId, "Upcoming Gathering");
    publishedEvent.setVenue("City Hall");
    publishedEvent.setEventDate(LocalDate.now().plusDays(5));
    publishedEvent.setEventTime(LocalTime.of(19, 0));
    publishedEvent.setCapacity(15);
    publishedEvent.setRegistrationDeadline(Instant.now().plusSeconds(86400 * 2));
    publishedEvent.publish();
    publishedEvent = eventRepository.save(publishedEvent);
    publishedEventId = publishedEvent.getId();

    // Event 3: Another completed event (for cross-event test)
    Event otherEvent = new Event(circleId, "Iliad Review");
    otherEvent.setVenue("Library");
    otherEvent.setEventDate(LocalDate.now().minusDays(10));
    otherEvent.setEventTime(LocalTime.of(17, 0));
    otherEvent.setCapacity(10);
    otherEvent.setRegistrationDeadline(Instant.now().minusSeconds(86400 * 11));
    otherEvent.publish();
    otherEvent.complete();
    otherEvent = eventRepository.save(otherEvent);
    otherEventId = otherEvent.getId();
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders h = new HttpHeaders();
    if (token != null) {
      h.setBearerAuth(token);
    }
    return h;
  }

  @Test
  void uploadToNonCompletedEventIsRejectedWith422() {
    AddGalleryMediaRequest req =
        new AddGalleryMediaRequest(MediaType.PHOTO, "https://example.com/photo1.jpg", "First meetup");

    ResponseEntity<String> resp =
        rest.exchange(
            "/api/events/" + publishedEventId + "/gallery",
            HttpMethod.POST,
            new HttpEntity<>(req, bearer(organizerToken)),
            String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void uploadToCompletedEventSucceedsForOrganizer() {
    AddGalleryMediaRequest req =
        new AddGalleryMediaRequest(MediaType.PHOTO, "https://example.com/photo1.jpg", "Group discussion");

    ResponseEntity<GalleryItemDto> resp =
        rest.exchange(
            "/api/events/" + completedEventId + "/gallery",
            HttpMethod.POST,
            new HttpEntity<>(req, bearer(organizerToken)),
            GalleryItemDto.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(resp.getBody()).isNotNull();
    assertThat(resp.getBody().mediaType()).isEqualTo(MediaType.PHOTO);
    assertThat(resp.getBody().mediaUrl()).isEqualTo("https://example.com/photo1.jpg");
    assertThat(resp.getBody().caption()).isEqualTo("Group discussion");
  }

  @Test
  void regularReaderCannotUploadToGallery() {
    AddGalleryMediaRequest req =
        new AddGalleryMediaRequest(MediaType.PHOTO, "https://example.com/photo1.jpg", "Sneak peek");

    ResponseEntity<String> resp =
        rest.exchange(
            "/api/events/" + completedEventId + "/gallery",
            HttpMethod.POST,
            new HttpEntity<>(req, bearer(memberToken)),
            String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void otherOrganizerCannotUploadToThisCircleEvent() {
    AddGalleryMediaRequest req =
        new AddGalleryMediaRequest(MediaType.PHOTO, "https://example.com/photo1.jpg", "Cross upload");

    ResponseEntity<String> resp =
        rest.exchange(
            "/api/events/" + completedEventId + "/gallery",
            HttpMethod.POST,
            new HttpEntity<>(req, bearer(otherOrgToken)),
            String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void memberCanReadGalleryAndNonMemberIsForbidden() {
    // Seed an item
    AddGalleryMediaRequest photo =
        new AddGalleryMediaRequest(MediaType.PHOTO, "https://example.com/photo.jpg", "Discussion");
    rest.exchange(
        "/api/events/" + completedEventId + "/gallery",
        HttpMethod.POST,
        new HttpEntity<>(photo, bearer(organizerToken)),
        GalleryItemDto.class);

    // Member read -> 200
    ResponseEntity<List<GalleryItemDto>> memberRead =
        rest.exchange(
            "/api/events/" + completedEventId + "/gallery",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            new ParameterizedTypeReference<>() {});

    assertThat(memberRead.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(memberRead.getBody()).hasSize(1);
    assertThat(memberRead.getBody().get(0).mediaUrl()).isEqualTo("https://example.com/photo.jpg");

    // Non-member read -> 403
    ResponseEntity<String> nonMemberRead =
        rest.exchange(
            "/api/events/" + completedEventId + "/gallery",
            HttpMethod.GET,
            new HttpEntity<>(bearer(nonMemberToken)),
            String.class);

    assertThat(nonMemberRead.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void crossEventIsolationNeverReturnsItemsFromOtherEvents() {
    // Add item to Event 1
    AddGalleryMediaRequest itemA =
        new AddGalleryMediaRequest(MediaType.PHOTO, "https://example.com/eventA.jpg", "Event A memory");
    rest.exchange(
        "/api/events/" + completedEventId + "/gallery",
        HttpMethod.POST,
        new HttpEntity<>(itemA, bearer(organizerToken)),
        GalleryItemDto.class);

    // Add item to Event 2
    AddGalleryMediaRequest itemB =
        new AddGalleryMediaRequest(MediaType.VIDEO, "https://example.com/eventB.mp4", "Event B video");
    rest.exchange(
        "/api/events/" + otherEventId + "/gallery",
        HttpMethod.POST,
        new HttpEntity<>(itemB, bearer(organizerToken)),
        GalleryItemDto.class);

    // Read Event 1 -> contains only eventA.jpg
    ResponseEntity<List<GalleryItemDto>> respA =
        rest.exchange(
            "/api/events/" + completedEventId + "/gallery",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            new ParameterizedTypeReference<>() {});

    assertThat(respA.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(respA.getBody()).hasSize(1);
    assertThat(respA.getBody().get(0).mediaUrl()).isEqualTo("https://example.com/eventA.jpg");
  }

  @Test
  void organizerCanDeleteItemAndReaderCannot() {
    AddGalleryMediaRequest item =
        new AddGalleryMediaRequest(MediaType.PHOTO, "https://example.com/delete_me.jpg", "Temporary");
    ResponseEntity<GalleryItemDto> createResp =
        rest.exchange(
            "/api/events/" + completedEventId + "/gallery",
            HttpMethod.POST,
            new HttpEntity<>(item, bearer(organizerToken)),
            GalleryItemDto.class);

    UUID itemId = createResp.getBody().id();

    // Reader attempts delete -> 403
    ResponseEntity<String> readerDelete =
        rest.exchange(
            "/api/gallery/" + itemId,
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(memberToken)),
            String.class);
    assertThat(readerDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // Organizer deletes -> 204
    ResponseEntity<Void> orgDelete =
        rest.exchange(
            "/api/gallery/" + itemId,
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(organizerToken)),
            Void.class);
    assertThat(orgDelete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // Confirm deleted from repository
    assertThat(galleryRepository.findById(itemId)).isEmpty();
  }

  @Test
  void invalidUrlSchemeIsRejectedWith400() {
    AddGalleryMediaRequest badReq =
        new AddGalleryMediaRequest(MediaType.PHOTO, "ftp://example.com/file.jpg", "Bad URL");

    ResponseEntity<String> resp =
        rest.exchange(
            "/api/events/" + completedEventId + "/gallery",
            HttpMethod.POST,
            new HttpEntity<>(badReq, bearer(organizerToken)),
            String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
