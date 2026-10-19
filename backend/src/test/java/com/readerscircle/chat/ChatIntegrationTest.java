package com.readerscircle.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.readerscircle.auth.JwtService;
import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.chat.dto.ChatMessageDto;
import com.readerscircle.chat.dto.SendMessageRequest;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.CircleStatus;
import com.readerscircle.circles.Membership;
import com.readerscircle.circles.MembershipRepository;
import com.readerscircle.circles.MembershipStatus;
import java.time.Instant;
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
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ChatIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("app.jwt.secret", () -> "chat-integration-test-secret-min-32-chars-long");
  }

  @Autowired TestRestTemplate rest;
  @Autowired JwtService jwt;
  @Autowired UserRepository userRepository;
  @Autowired CircleRepository circleRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired ChatMessageRepository chatMessageRepository;
  @Autowired ChatService chatService;
  @Autowired ChatChannelInterceptor channelInterceptor;

  private User admin;
  private User organizer;
  private User approvedMember;
  private User nonMember;
  private Circle activeCircle;
  private Circle inactiveCircle;

  @BeforeEach
  void setUp() {
    chatMessageRepository.deleteAll();
    membershipRepository.deleteAll();
    circleRepository.deleteAll();
    userRepository.deleteAll();

    admin = userRepository.save(new User("Admin User", "admin@chat.local", "hash", Role.ADMIN));
    organizer = userRepository.save(new User("Circle Organizer", "organizer@chat.local", "hash", Role.ORGANIZER));
    approvedMember = userRepository.save(new User("Approved Member", "member@chat.local", "hash", Role.READER));
    nonMember = userRepository.save(new User("Non Member", "stranger@chat.local", "hash", Role.READER));

    activeCircle = new Circle("Science Fiction Circle", "San Francisco", "Sci-fi readers club", organizer.getId());
    activeCircle.setStatus(CircleStatus.ACTIVE);
    activeCircle = circleRepository.save(activeCircle);

    inactiveCircle = new Circle("Inactive Circle", "Oakland", "Old club", organizer.getId());
    inactiveCircle.setStatus(CircleStatus.INACTIVE);
    inactiveCircle = circleRepository.save(inactiveCircle);

    Membership m = new Membership(activeCircle.getId(), approvedMember.getId());
    m.approve();
    membershipRepository.save(m);
  }

  private HttpHeaders authHeaders(User user) {
    HttpHeaders h = new HttpHeaders();
    h.setBearerAuth(jwt.generateAccessToken(user.getId(), user.getRole()));
    return h;
  }

  @Test
  void nonMemberCannotAccessChatHistory() {
    ResponseEntity<String> res =
        rest.exchange(
            "/api/circles/" + activeCircle.getId() + "/messages",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(nonMember)),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void approvedMemberCanReadHistoryAndSendMessage() {
    // Send message via service
    ChatMessageDto sent =
        chatService.sendMessage(
            activeCircle.getId(),
            approvedMember.getId(),
            approvedMember.getRole(),
            "Hello everyone in the sci-fi club!");

    assertThat(sent.id()).isNotNull();
    assertThat(sent.content()).isEqualTo("Hello everyone in the sci-fi club!");
    assertThat(sent.senderName()).isEqualTo("Approved Member");

    // Fetch history via REST API
    ResponseEntity<List<ChatMessageDto>> res =
        rest.exchange(
            "/api/circles/" + activeCircle.getId() + "/messages",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(approvedMember)),
            new ParameterizedTypeReference<>() {});

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody()).hasSize(1);
    assertThat(res.getBody().get(0).content()).isEqualTo("Hello everyone in the sci-fi club!");
  }

  @Test
  void organizerAndAdminCanAccessChatHistory() {
    chatService.sendMessage(
        activeCircle.getId(),
        approvedMember.getId(),
        approvedMember.getRole(),
        "Member message");

    ResponseEntity<List<ChatMessageDto>> orgRes =
        rest.exchange(
            "/api/circles/" + activeCircle.getId() + "/messages",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(organizer)),
            new ParameterizedTypeReference<>() {});
    assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(orgRes.getBody()).hasSize(1);

    ResponseEntity<List<ChatMessageDto>> adminRes =
        rest.exchange(
            "/api/circles/" + activeCircle.getId() + "/messages",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(admin)),
            new ParameterizedTypeReference<>() {});
    assertThat(adminRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(adminRes.getBody()).hasSize(1);
  }

  @Test
  void sendMessageValidatesContentLengthAndBlank() {
    // Blank message
    assertThatThrownBy(
            () ->
                chatService.sendMessage(
                    activeCircle.getId(),
                    approvedMember.getId(),
                    approvedMember.getRole(),
                    "   "))
        .isInstanceOf(ResponseStatusException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

    // Oversize message > 2000 chars
    String huge = "a".repeat(2001);
    assertThatThrownBy(
            () ->
                chatService.sendMessage(
                    activeCircle.getId(),
                    approvedMember.getId(),
                    approvedMember.getRole(),
                    huge))
        .isInstanceOf(ResponseStatusException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
  }

  @Test
  void cannotSendMessageToInactiveCircle() {
    assertThatThrownBy(
            () ->
                chatService.sendMessage(
                    inactiveCircle.getId(),
                    organizer.getId(),
                    organizer.getRole(),
                    "Attempting message in inactive circle"))
        .isInstanceOf(ResponseStatusException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
  }

  @Test
  void historyPaginationAndSinceFilter() throws InterruptedException {
    Instant t0 = Instant.now();
    Thread.sleep(10);
    chatService.sendMessage(activeCircle.getId(), approvedMember.getId(), approvedMember.getRole(), "Msg 1");
    Thread.sleep(10);
    Instant t1 = Instant.now();
    Thread.sleep(10);
    chatService.sendMessage(activeCircle.getId(), approvedMember.getId(), approvedMember.getRole(), "Msg 2");
    Thread.sleep(10);
    chatService.sendMessage(activeCircle.getId(), approvedMember.getId(), approvedMember.getRole(), "Msg 3");

    // All messages
    List<ChatMessageDto> all =
        chatService.getMessages(activeCircle.getId(), approvedMember.getId(), approvedMember.getRole(), null, 0, 10);
    assertThat(all).hasSize(3);
    assertThat(all.get(0).content()).isEqualTo("Msg 1");
    assertThat(all.get(1).content()).isEqualTo("Msg 2");
    assertThat(all.get(2).content()).isEqualTo("Msg 3");

    // Filter since t1
    List<ChatMessageDto> sinceT1 =
        chatService.getMessages(activeCircle.getId(), approvedMember.getId(), approvedMember.getRole(), t1, 0, 10);
    assertThat(sinceT1).hasSize(2);
    assertThat(sinceT1.get(0).content()).isEqualTo("Msg 2");
    assertThat(sinceT1.get(1).content()).isEqualTo("Msg 3");
  }

  @Test
  void moderationRulesForMessageDeletion() {
    ChatMessageDto msg1 =
        chatService.sendMessage(activeCircle.getId(), approvedMember.getId(), approvedMember.getRole(), "Msg to delete");

    // Non-member / other member cannot delete
    ResponseEntity<Void> forbiddenRes =
        rest.exchange(
            "/api/messages/" + msg1.id(),
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders(nonMember)),
            Void.class);
    assertThat(forbiddenRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // Organizer can delete
    ResponseEntity<Void> orgDeleteRes =
        rest.exchange(
            "/api/messages/" + msg1.id(),
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders(organizer)),
            Void.class);
    assertThat(orgDeleteRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // Message is now excluded from history
    List<ChatMessageDto> messages =
        chatService.getMessages(activeCircle.getId(), approvedMember.getId(), approvedMember.getRole(), null, 0, 10);
    assertThat(messages).isEmpty();

    // Reader can delete own message
    ChatMessageDto msg2 =
        chatService.sendMessage(activeCircle.getId(), approvedMember.getId(), approvedMember.getRole(), "Own message");
    ResponseEntity<Void> selfDeleteRes =
        rest.exchange(
            "/api/messages/" + msg2.id(),
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders(approvedMember)),
            Void.class);
    assertThat(selfDeleteRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void channelInterceptorEnforcesAuthorizationOnConnectSubscribeAndSend() {
    String memberToken = jwt.generateAccessToken(approvedMember.getId(), approvedMember.getRole());
    String nonMemberToken = jwt.generateAccessToken(nonMember.getId(), nonMember.getRole());

    // 1. CONNECT with valid token
    StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    connectAccessor.addNativeHeader("Authorization", "Bearer " + memberToken);
    Message<?> connectMsg = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
    Message<?> connectedMsg = channelInterceptor.preSend(connectMsg, null);
    StompHeaderAccessor connectedAccessor = MessageHeaderAccessor.getAccessor(connectedMsg, StompHeaderAccessor.class);
    assertThat(connectedAccessor).isNotNull();
    assertThat(connectedAccessor.getUser()).isNotNull();
    assertThat(connectedAccessor.getUser().getName()).isEqualTo(approvedMember.getId().toString());

    // 2. SUBSCRIBE to active circle: Member succeeds
    StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    subAccessor.setDestination("/topic/circles/" + activeCircle.getId());
    subAccessor.setUser(connectedAccessor.getUser());
    Message<?> subMsg = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());
    channelInterceptor.preSend(subMsg, null); // should not throw

    // 3. SUBSCRIBE to active circle: Non-member throws AccessDeniedException
    StompHeaderAccessor nonMemberConnectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    nonMemberConnectAccessor.addNativeHeader("Authorization", "Bearer " + nonMemberToken);
    Message<?> nonMemberConnectedMsg =
        channelInterceptor.preSend(MessageBuilder.createMessage(new byte[0], nonMemberConnectAccessor.getMessageHeaders()), null);
    StompHeaderAccessor nonMemberConnectedAccessor =
        MessageHeaderAccessor.getAccessor(nonMemberConnectedMsg, StompHeaderAccessor.class);

    StompHeaderAccessor nonMemberSubAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    nonMemberSubAccessor.setDestination("/topic/circles/" + activeCircle.getId());
    nonMemberSubAccessor.setUser(nonMemberConnectedAccessor.getUser());
    Message<?> nonMemberSubMsg = MessageBuilder.createMessage(new byte[0], nonMemberSubAccessor.getMessageHeaders());

    assertThatThrownBy(() -> channelInterceptor.preSend(nonMemberSubMsg, null))
        .isInstanceOf(AccessDeniedException.class);

    // 4. SEND to active circle: Non-member throws AccessDeniedException
    StompHeaderAccessor nonMemberSendAccessor = StompHeaderAccessor.create(StompCommand.SEND);
    nonMemberSendAccessor.setDestination("/app/circles/" + activeCircle.getId() + "/send");
    nonMemberSendAccessor.setUser(nonMemberConnectedAccessor.getUser());
    Message<?> nonMemberSendMsg = MessageBuilder.createMessage(new byte[0], nonMemberSendAccessor.getMessageHeaders());

    assertThatThrownBy(() -> channelInterceptor.preSend(nonMemberSendMsg, null))
        .isInstanceOf(AccessDeniedException.class);
  }
}
