package com.readerscircle.chat;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.chat.dto.ChatDeleteEventDto;
import com.readerscircle.chat.dto.ChatMessageDto;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.CircleStatus;
import com.readerscircle.circles.Membership;
import com.readerscircle.circles.MembershipRepository;
import com.readerscircle.circles.MembershipStatus;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

  public static final int MAX_MESSAGE_LENGTH = 2000;
  public static final int MAX_PAGE_SIZE = 100;
  public static final int DEFAULT_PAGE_SIZE = 30;

  private final ChatMessageRepository chatMessageRepository;
  private final CircleRepository circleRepository;
  private final MembershipRepository membershipRepository;
  private final UserRepository userRepository;
  private final MessagePublisher messagePublisher;
  private final MessageLikeRepository messageLikeRepository;
  private final ChatReadStateRepository chatReadStateRepository;

  @Autowired
  public ChatService(
      ChatMessageRepository chatMessageRepository,
      CircleRepository circleRepository,
      MembershipRepository membershipRepository,
      UserRepository userRepository,
      MessagePublisher messagePublisher,
      MessageLikeRepository messageLikeRepository,
      ChatReadStateRepository chatReadStateRepository) {
    this.chatMessageRepository = chatMessageRepository;
    this.circleRepository = circleRepository;
    this.membershipRepository = membershipRepository;
    this.userRepository = userRepository;
    this.messagePublisher = messagePublisher;
    this.messageLikeRepository = messageLikeRepository;
    this.chatReadStateRepository = chatReadStateRepository;
  }

  /** Backwards-compatible constructor for callers/tests wiring 5 deps. */
  public ChatService(
      ChatMessageRepository chatMessageRepository,
      CircleRepository circleRepository,
      MembershipRepository membershipRepository,
      UserRepository userRepository,
      MessagePublisher messagePublisher) {
    this(chatMessageRepository, circleRepository, membershipRepository, userRepository, messagePublisher, null, null);
  }

  /**
   * Asserts that the user is an approved member of the circle, the circle's organizer, or an Admin.
   * Blocked users are rejected everywhere (chat history, send, WebSocket).
   */
  public Circle assertCanAccess(UUID circleId, UUID userId, Role role) {    boolean callerActive =
        userId == null || userRepository.findById(userId).map(User::isActive).orElse(false);
    if (!callerActive) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Your account has been blocked by the administrator.");
    }

    Circle circle =
        circleRepository
            .findById(circleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    // Hidden channels (Organisers Lounge) are role-gated, not just
    // membership-gated: the role is read from the DB so a demotion cuts
    // access immediately, even with a stale membership row or JWT.
    if (circle.isHidden()) {
      Role dbRole =
          userId == null
              ? null
              : userRepository.findById(userId).map(User::getRole).orElse(null);
      if (dbRole != Role.ORGANIZER && dbRole != Role.ADMIN) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "This channel is reserved for organisers and admins");
      }
      return circle;
    }

    if (role == Role.ADMIN) {
      return circle;
    }

    if (isEffectiveOrganizer(circle, userId)) {
      return circle;
    }

    Membership membership =
        membershipRepository
            .findByCircleIdAndReaderId(circleId, userId)
            .orElse(null);

    if (membership == null || membership.getStatus() != MembershipStatus.APPROVED) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only approved members of this circle can access the chat");
    }

    return circle;
  }

  /**
   * Effective organizer = assigned as the circle's organizer AND still holding
   * a live ORGANIZER/ADMIN role. Role is read from the DB (not the JWT) so a
   * demotion takes effect immediately, even for tokens minted before it.
   */
  private boolean isEffectiveOrganizer(Circle circle, UUID userId) {
    if (circle == null || userId == null || !userId.equals(circle.getOrganizerId())) {
      return false;
    }
    Role role = userRepository.findById(userId).map(User::getRole).orElse(null);
    return role == Role.ORGANIZER || role == Role.ADMIN;
  }

  /**
   * Asserts access and verifies the circle is ACTIVE to allow sending messages.
   */
  public Circle assertCanSend(UUID circleId, UUID userId, Role role) {
    Circle circle = assertCanAccess(circleId, userId, role);

    if (circle.getStatus() != CircleStatus.ACTIVE) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Cannot send messages to an inactive circle");
    }

    return circle;
  }

  @Transactional
  public ChatMessageDto sendMessage(UUID circleId, UUID senderId, Role senderRole, String content) {
    return sendMessage(circleId, senderId, senderRole, content, null);
  }

  @Transactional
  public ChatMessageDto sendMessage(
      UUID circleId, UUID senderId, Role senderRole, String content, String attachmentUrl) {
    String text = content == null ? "" : content.trim();
    String attachment = attachmentUrl == null || attachmentUrl.isBlank() ? null : attachmentUrl.trim();
    if (text.isEmpty() && attachment == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content must not be blank");
    }
    if (text.length() > MAX_MESSAGE_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Message content must not exceed " + MAX_MESSAGE_LENGTH + " characters");
    }

    assertCanSend(circleId, senderId, senderRole);

    ChatMessage entity = new ChatMessage(circleId, senderId, text);
    entity.setAttachmentUrl(attachment);
    ChatMessage saved = chatMessageRepository.save(entity);

    String senderName =
        userRepository
            .findById(senderId)
            .map(User::getName)
            .orElse("Member");

    ChatMessageDto dto = toDto(saved, senderName, 0, false);
    messagePublisher.publishMessage(circleId, dto);

    return dto;
  }

  @Transactional(readOnly = true)
  public List<ChatMessageDto> getMessages(
      UUID circleId, UUID userId, Role role, Instant since, int page, int size) {
    assertCanAccess(circleId, userId, role);

    int resolvedSize = Math.max(1, Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE));

    List<ChatMessage> entities;
    if (since != null) {
      entities =
          chatMessageRepository.findByCircleIdAndDeletedFalseAndSentAtAfterOrderBySentAtAsc(
              circleId, since);
    } else {
      Pageable pageable = PageRequest.of(Math.max(0, page), resolvedSize, Sort.by("sentAt").ascending());
      Page<ChatMessage> messagePage =
          chatMessageRepository.findByCircleIdAndDeletedFalseOrderBySentAtAsc(circleId, pageable);
      entities = messagePage.getContent();
    }

    if (entities.isEmpty()) {
      return Collections.emptyList();
    }

    List<UUID> senderIds = entities.stream().map(ChatMessage::getSenderId).distinct().toList();
    List<User> senders = userRepository.findAllById(senderIds);
    Map<UUID, String> senderNames =
        senders.stream().collect(Collectors.toMap(User::getId, User::getName));
    Map<UUID, String> senderAvatars =
        senders.stream()
            .filter(u -> u.getProfilePictureUrl() != null)
            .collect(Collectors.toMap(User::getId, User::getProfilePictureUrl));

    Map<UUID, Long> likeCounts = likeCounts(entities.stream().map(ChatMessage::getId).toList());
    java.util.Set<UUID> likedByMe =
        messageLikeRepository != null && userId != null
            ? messageLikeRepository.findByMessageIdIn(entities.stream().map(ChatMessage::getId).toList()).stream()
                .filter(l -> l.getUserId().equals(userId))
                .map(MessageLike::getMessageId)
                .collect(java.util.stream.Collectors.toSet())
            : java.util.Collections.emptySet();
    Map<UUID, Map<String, Long>> reactionCounts = reactionCounts(entities.stream().map(ChatMessage::getId).toList());
    Map<UUID, List<String>> myReactionMap = myReactions(entities.stream().map(ChatMessage::getId).toList(), userId);

    return entities.stream()
        .map(msg -> toDto(msg, senderNames.getOrDefault(msg.getSenderId(), "Member"),
            senderAvatars.get(msg.getSenderId()),
            likeCounts.getOrDefault(msg.getId(), 0L), likedByMe.contains(msg.getId()),
            reactionCounts.getOrDefault(msg.getId(), java.util.Map.of()),
            myReactionMap.getOrDefault(msg.getId(), java.util.List.of())))
        .toList();
  }

  /**
   * Message search inside a conversation (reader header search icon).
   * Member-gated, newest last, capped.
   */
  @Transactional(readOnly = true)
  public List<ChatMessageDto> searchMessages(
      UUID circleId, UUID userId, Role role, String query, int limit) {
    assertCanAccess(circleId, userId, role);
    String q = query == null ? "" : query.trim().toLowerCase();
    if (q.isEmpty()) {
      return Collections.emptyList();
    }
    int lim = Math.max(1, Math.min(limit <= 0 ? 30 : limit, 50));
    List<ChatMessage> hits = new java.util.ArrayList<>();
    for (ChatMessage m :
        chatMessageRepository.findByCircleIdAndDeletedFalseOrderBySentAtAsc(
            circleId, PageRequest.of(0, MAX_PAGE_SIZE, Sort.by("sentAt").ascending())).getContent()) {
      if (m.getContent() != null && m.getContent().toLowerCase().contains(q)) {
        hits.add(m);
      }
    }
    List<ChatMessage> page = hits.stream().limit(lim).toList();
    if (page.isEmpty()) {
      return Collections.emptyList();
    }
    List<UUID> senderIds = page.stream().map(ChatMessage::getSenderId).distinct().toList();
    List<User> senders = userRepository.findAllById(senderIds);
    Map<UUID, String> senderNames =
        senders.stream().collect(Collectors.toMap(User::getId, User::getName));
    Map<UUID, String> senderAvatars =
        senders.stream()
            .filter(u -> u.getProfilePictureUrl() != null)
            .collect(Collectors.toMap(User::getId, User::getProfilePictureUrl));
    Map<UUID, Long> likeCounts = likeCounts(page.stream().map(ChatMessage::getId).toList());
    Map<UUID, Map<String, Long>> reactionCounts =
        reactionCounts(page.stream().map(ChatMessage::getId).toList());
    Map<UUID, List<String>> myReactionMap =
        myReactions(page.stream().map(ChatMessage::getId).toList(), userId);
    java.util.Set<UUID> likedByMe =
        messageLikeRepository != null && userId != null
            ? messageLikeRepository.findByMessageIdIn(page.stream().map(ChatMessage::getId).toList()).stream()
                .filter(l -> l.getUserId().equals(userId))
                .map(MessageLike::getMessageId)
                .collect(java.util.stream.Collectors.toSet())
            : java.util.Collections.emptySet();
    return page.stream()
        .map(msg -> toDto(msg, senderNames.getOrDefault(msg.getSenderId(), "Member"),
            senderAvatars.get(msg.getSenderId()),
            likeCounts.getOrDefault(msg.getId(), 0L), likedByMe.contains(msg.getId()),
            reactionCounts.getOrDefault(msg.getId(), java.util.Map.of()),
            myReactionMap.getOrDefault(msg.getId(), java.util.List.of())))
        .toList();
  }

  /** Toggle one emoji reaction (👍 ❤️ 👏 …). Returns per-emoji counts + mine. */
  @Transactional
  public Map<String, Object> toggleReaction(
      UUID messageId, UUID callerId, Role callerRole, String emoji) {
    ChatMessage message = visibleMessage(messageId);
    assertCanAccess(message.getCircleId(), callerId, callerRole);
    String e = normalizeEmoji(emoji);
    boolean active;
    if (messageLikeRepository.existsByMessageIdAndUserIdAndEmoji(messageId, callerId, e)) {
      messageLikeRepository.deleteByMessageIdAndUserIdAndEmoji(messageId, callerId, e);
      active = false;
    } else {
      try {
        messageLikeRepository.save(new MessageLike(messageId, callerId, e));
      } catch (org.springframework.dao.DataIntegrityViolationException race) {
        // lost a race with another toggle — treat as toggled on
      }
      active = true;
    }
    Map<String, Long> counts = reactionCounts(java.util.List.of(messageId))
        .getOrDefault(messageId, java.util.Map.of());
    List<String> mine = myReactions(java.util.List.of(messageId), callerId)
        .getOrDefault(messageId, java.util.List.of());
    // Live-update bubbles for WS subscribers.
    try {
      messagePublisher.publishMessage(
          message.getCircleId(),
          toDto(message, senderNameOf(message.getSenderId()), senderAvatarOf(message.getSenderId()),
              likeCountOf(message.getId()), likedBy(message.getId(), callerId), counts, mine));
    } catch (Exception ignored) {
    }
    Map<String, Object> out = new HashMap<>();
    out.put("reactions", counts);
    out.put("myReactions", mine);
    out.put("active", active);
    out.put("emoji", e);
    out.put("likeCount", likeCountOf(messageId));
    return out;
  }

  static final java.util.Set<String> ALLOWED_EMOJI =
      java.util.Set.of("❤", "❤️", "👍", "👏", "😂", "😮", "😢", "🙏");

  static String normalizeEmoji(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Emoji is required");
    }
    String e = raw.trim();
    if (!ALLOWED_EMOJI.contains(e)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported reaction");
    }
    return e.equals("❤️") ? "❤" : e;
  }

  /** Newest non-deleted message in a circle (channel-list previews). */
  @Transactional(readOnly = true)
  public java.util.Optional<ChatMessageDto> latestMessage(
      UUID circleId, UUID userId, Role role) {
    assertCanAccess(circleId, userId, role);
    return chatMessageRepository
        .findFirstByCircleIdAndDeletedFalseOrderBySentAtDesc(circleId)
        .map(
            msg ->
                toDto(
                    msg,
                    senderNameOf(msg.getSenderId()),
                    likeCountOf(msg.getId()),
                    likedBy(msg.getId(), userId)));
  }

  /** Edit own message (or any, as organizer/admin). Broadcasts the update. */
  @Transactional
  public ChatMessageDto editMessage(UUID messageId, UUID callerId, Role callerRole, String content) {
    ChatMessage message =
        chatMessageRepository
            .findById(messageId)
            .filter(m -> !m.isDeleted())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

    Circle circle =
        circleRepository
            .findById(message.getCircleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    boolean isSender = callerId != null && callerId.equals(message.getSenderId());
    boolean isOrganizer = callerId != null && callerId.equals(circle.getOrganizerId());
    boolean isAdmin = callerRole == Role.ADMIN;

    if (!isSender && !isOrganizer && !isAdmin) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You do not have permission to edit this message");
    }

    String text = content == null ? "" : content.trim();
    if (text.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content must not be blank");
    }
    if (text.length() > MAX_MESSAGE_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Message content must not exceed " + MAX_MESSAGE_LENGTH + " characters");
    }

    message.setContent(text);
    message.setEditedAt(Instant.now());
    ChatMessage saved = chatMessageRepository.save(message);
    ChatMessageDto dto = toDto(saved, senderNameOf(saved.getSenderId()),
        likeCountOf(saved.getId()), likedBy(saved.getId(), callerId));
    messagePublisher.publishMessage(circle.getId(), dto);
    return dto;
  }

  /** Heart a bubble (idempotent). Caller must be able to read the circle. */
  @Transactional
  public long likeMessage(UUID messageId, UUID callerId, Role callerRole) {
    ChatMessage message = visibleMessage(messageId);
    assertCanAccess(message.getCircleId(), callerId, callerRole);
    if (messageLikeRepository != null
        && !messageLikeRepository.existsByMessageIdAndUserId(messageId, callerId)) {
      messageLikeRepository.save(new MessageLike(messageId, callerId));
    }
    return likeCountOf(messageId);
  }

  @Transactional
  public long unlikeMessage(UUID messageId, UUID callerId, Role callerRole) {
    ChatMessage message = visibleMessage(messageId);
    assertCanAccess(message.getCircleId(), callerId, callerRole);
    if (messageLikeRepository != null) {
      messageLikeRepository.deleteByMessageIdAndUserId(messageId, callerId);
    }
    return likeCountOf(messageId);
  }

  /** Ephemeral typing indicator: access-checked, broadcast, never persisted. */
  @Transactional(readOnly = true)
  public void broadcastTyping(UUID circleId, UUID senderId, Role senderRole) {
    assertCanAccess(circleId, senderId, senderRole);
    messagePublisher.publishTyping(circleId, senderId, senderNameOf(senderId));
  }

  /** Per-user per-circle read watermark (defaults to now). */
  @Transactional
  public void markRead(UUID circleId, UUID readerId, Role role, Instant lastReadAt) {
    assertCanAccess(circleId, readerId, role);
    if (chatReadStateRepository == null) return;
    Instant at = lastReadAt != null ? lastReadAt : Instant.now();
    ChatReadState state =
        chatReadStateRepository
            .findByCircleIdAndReaderId(circleId, readerId)
            .orElseGet(() -> new ChatReadState(circleId, readerId, at));
    state.setLastReadAt(at);
    chatReadStateRepository.save(state);
  }

  /** Unread counts per accessible circle (excludes own messages). */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> unreadCounts(UUID readerId, Role role) {
    java.util.Set<UUID> circleIds = new java.util.LinkedHashSet<>();
    if (role == Role.ADMIN) {
      circleRepository.findAll().forEach(c -> circleIds.add(c.getId()));
    } else {
      circleRepository.findByOrganizerId(readerId).forEach(c -> circleIds.add(c.getId()));
      membershipRepository.findByReaderId(readerId).stream()
          .filter(m -> m.getStatus() == MembershipStatus.APPROVED)
          .forEach(m -> circleIds.add(m.getCircleId()));
    }
    Map<UUID, Instant> watermarks = new HashMap<>();
    if (chatReadStateRepository != null) {
      chatReadStateRepository.findByReaderId(readerId)
          .forEach(s -> watermarks.put(s.getCircleId(), s.getLastReadAt()));
    }
    List<Map<String, Object>> out = new java.util.ArrayList<>();
    for (UUID circleId : circleIds) {
      Instant since = watermarks.getOrDefault(circleId, Instant.EPOCH);
      long unread =
          chatMessageRepository.findByCircleIdAndDeletedFalseAndSentAtAfterOrderBySentAtAsc(circleId, since)
              .stream()
              .filter(m -> !m.getSenderId().equals(readerId))
              .count();
      Map<String, Object> row = new HashMap<>();
      row.put("circleId", circleId.toString());
      row.put("unreadCount", unread);
      out.add(row);
    }
    return out;
  }

  @Transactional
  public void deleteMessage(UUID messageId, UUID callerId, Role callerRole) {
    ChatMessage message =
        chatMessageRepository
            .findById(messageId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

    Circle circle =
        circleRepository
            .findById(message.getCircleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    boolean isSender = callerId != null && callerId.equals(message.getSenderId());
    boolean isOrganizer = callerId != null && callerId.equals(circle.getOrganizerId());
    boolean isAdmin = callerRole == Role.ADMIN;

    if (!isSender && !isOrganizer && !isAdmin) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You do not have permission to delete this message");
    }

    message.setDeleted(true);
    chatMessageRepository.save(message);

    messagePublisher.publishDeletion(
        circle.getId(), new ChatDeleteEventDto(messageId, circle.getId(), true));
  }

  private ChatMessageDto toDto(ChatMessage message, String senderName) {
    return toDto(message, senderName, likeCountOf(message.getId()), false);
  }

  private ChatMessageDto toDto(ChatMessage message, String senderName, long likeCount, boolean likedByMe) {
    return toDto(message, senderName, senderAvatarOf(message.getSenderId()), likeCount, likedByMe,
        reactionCounts(java.util.List.of(message.getId())).getOrDefault(message.getId(), java.util.Map.of()),
        java.util.List.of());
  }

  private ChatMessageDto toDto(
      ChatMessage message,
      String senderName,
      String senderAvatarUrl,
      long likeCount,
      boolean likedByMe,
      Map<String, Long> reactions,
      List<String> myReactions) {
    return new ChatMessageDto(
        message.getId(),
        message.getCircleId(),
        message.getSenderId(),
        senderName,
        senderAvatarUrl,
        message.getContent(),
        message.getSentAt(),
        message.getEditedAt(),
        message.isDeleted(),
        message.getAttachmentUrl(),
        likeCount,
        likedByMe,
        reactions,
        myReactions);
  }

  private ChatMessage visibleMessage(UUID messageId) {
    return chatMessageRepository
        .findById(messageId)
        .filter(m -> !m.isDeleted())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
  }

  private long likeCountOf(UUID messageId) {
    return messageLikeRepository != null ? messageLikeRepository.countByMessageId(messageId) : 0;
  }

  private boolean likedBy(UUID messageId, UUID userId) {
    return messageLikeRepository != null
        && userId != null
        && messageLikeRepository.existsByMessageIdAndUserId(messageId, userId);
  }

  private String senderNameOf(UUID senderId) {
    return userRepository.findById(senderId).map(User::getName).orElse("Member");
  }

  private String senderAvatarOf(UUID senderId) {
    if (senderId == null) return null;
    return userRepository.findById(senderId).map(User::getProfilePictureUrl).orElse(null);
  }

  private Map<UUID, Long> likeCounts(List<UUID> messageIds) {
    Map<UUID, Long> counts = new HashMap<>();
    if (messageLikeRepository == null || messageIds.isEmpty()) return counts;
    messageLikeRepository.findByMessageIdIn(messageIds)
        .forEach(l -> counts.merge(l.getMessageId(), 1L, Long::sum));
    return counts;
  }

  private Map<UUID, Map<String, Long>> reactionCounts(List<UUID> messageIds) {
    Map<UUID, Map<String, Long>> counts = new HashMap<>();
    if (messageLikeRepository == null || messageIds.isEmpty()) return counts;
    for (MessageLike l : messageLikeRepository.findByMessageIdIn(messageIds)) {
      counts
          .computeIfAbsent(l.getMessageId(), k -> new HashMap<>())
          .merge(l.getEmoji() == null ? "❤" : l.getEmoji(), 1L, Long::sum);
    }
    return counts;
  }

  private Map<UUID, List<String>> myReactions(List<UUID> messageIds, UUID userId) {
    Map<UUID, List<String>> mine = new HashMap<>();
    if (messageLikeRepository == null || messageIds.isEmpty() || userId == null) return mine;
    for (MessageLike l : messageLikeRepository.findByMessageIdIn(messageIds)) {
      if (l.getUserId().equals(userId)) {
        mine.computeIfAbsent(l.getMessageId(), k -> new java.util.ArrayList<>())
            .add(l.getEmoji() == null ? "❤" : l.getEmoji());
      }
    }
    return mine;
  }
}
