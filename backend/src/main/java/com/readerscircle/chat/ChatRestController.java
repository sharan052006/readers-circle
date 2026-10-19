package com.readerscircle.chat;

import com.readerscircle.auth.Role;
import com.readerscircle.chat.dto.ChatMessageDto;
import com.readerscircle.chat.dto.SendMessageRequest;
import com.readerscircle.gallery.FileStorageService;
import com.readerscircle.gallery.MediaType;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ChatRestController {

  private final ChatService chatService;
  private final FileStorageService fileStorageService;

  public ChatRestController(ChatService chatService, FileStorageService fileStorageService) {
    this.chatService = chatService;
    this.fileStorageService = fileStorageService;
  }

  @GetMapping("/api/circles/{circleId}/messages")
  @PreAuthorize("isAuthenticated()")
  public List<ChatMessageDto> getMessages(
      @PathVariable UUID circleId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size,
      Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role callerRole = getUserRole(authentication);
    return chatService.getMessages(circleId, callerId, callerRole, since, page, size);
  }

  @DeleteMapping("/api/messages/{messageId}")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteMessage(
      @PathVariable UUID messageId,
      Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role callerRole = getUserRole(authentication);
    chatService.deleteMessage(messageId, callerId, callerRole);
  }

  /** Plain-HTTP send fallback (same rules as the WebSocket send). */
  @PostMapping("/api/circles/{circleId}/messages")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.CREATED)
  public ChatMessageDto sendMessage(
      @PathVariable UUID circleId,
      @Valid @RequestBody SendMessageRequest request,
      Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role callerRole = getUserRole(authentication);
    return chatService.sendMessage(circleId, callerId, callerRole, request.content(), request.attachmentUrl());
  }

  /** Edit a message (sender, circle organizer, or admin). */
  @PatchMapping("/api/messages/{messageId}")
  @PreAuthorize("isAuthenticated()")
  public ChatMessageDto editMessage(
      @PathVariable UUID messageId,
      @Valid @RequestBody SendMessageRequest request,
      Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role callerRole = getUserRole(authentication);
    return chatService.editMessage(messageId, callerId, callerRole, request.content());
  }

  /** Heart a bubble (idempotent) — returns the new like count. */
  @PostMapping("/api/messages/{messageId}/like")
  @PreAuthorize("isAuthenticated()")
  public Map<String, Long> likeMessage(
      @PathVariable UUID messageId,
      Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role callerRole = getUserRole(authentication);
    return Map.of("likeCount", chatService.likeMessage(messageId, callerId, callerRole));
  }

  /** Toggle one emoji reaction (👍 ❤️ 👏 …) — returns per-emoji counts. */
  @PostMapping("/api/messages/{messageId}/reactions")
  @PreAuthorize("isAuthenticated()")
  public Map<String, Object> toggleReaction(
      @PathVariable UUID messageId,
      @RequestBody(required = false) Map<String, String> body,
      Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role callerRole = getUserRole(authentication);
    String emoji = body == null ? null : body.getOrDefault("emoji", body.get("reaction"));
    return chatService.toggleReaction(messageId, callerId, callerRole, emoji);
  }

  /** Search inside one conversation (reader header search icon). */
  @GetMapping("/api/circles/{circleId}/messages/search")
  @PreAuthorize("isAuthenticated()")
  public List<ChatMessageDto> searchMessages(
      @PathVariable UUID circleId,
      @RequestParam(name = "q", required = false, defaultValue = "") String q,
      @RequestParam(defaultValue = "30") int limit,
      Authentication authentication) {
    return chatService.searchMessages(
        circleId, getUserId(authentication), getUserRole(authentication), q, limit);
  }

  @DeleteMapping("/api/messages/{messageId}/like")
  @PreAuthorize("isAuthenticated()")
  public Map<String, Long> unlikeMessage(
      @PathVariable UUID messageId,
      Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role callerRole = getUserRole(authentication);
    return Map.of("likeCount", chatService.unlikeMessage(messageId, callerId, callerRole));
  }

  /** Image attach for the message composer (returns an /api/media URL to send with the message). */
  @PostMapping(
      value = "/api/chat/attachments",
      consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, String> uploadAttachment(@RequestParam("file") MultipartFile file) {
    String url = fileStorageService.storeFile(file, MediaType.PHOTO);
    return Map.of("url", url);
  }

  /** Record read watermark for unread badges (defaults to now). */
  @PostMapping("/api/circles/{circleId}/read")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markRead(
      @PathVariable UUID circleId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastReadAt,
      Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role callerRole = getUserRole(authentication);
    chatService.markRead(circleId, callerId, callerRole, lastReadAt);
  }

  /** Unread counts per accessible circle (excludes own messages). */
  @GetMapping("/api/chat/unread")
  @PreAuthorize("isAuthenticated()")
  public List<Map<String, Object>> unreadCounts(Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role callerRole = getUserRole(authentication);
    return chatService.unreadCounts(callerId, callerRole);
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
