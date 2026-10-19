package com.readerscircle.discussions;

import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.discussions.dto.CommentDto;
import com.readerscircle.discussions.dto.CreateDiscussionRequest;
import com.readerscircle.discussions.dto.DiscussionDto;
import com.readerscircle.discussions.dto.ThreadDto;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Book Discussions backend: list / create / view / threads / comments / likes.
 * Response shapes are camelCase to match the discussions UI contract.
 */
@Service
public class DiscussionService {

  private final DiscussionRepository discussions;
  private final DiscussionThreadRepository threads;
  private final DiscussionCommentRepository comments;
  private final DiscussionThreadLikeRepository likes;
  private final DiscussionLikeRepository discussionLikes;
  private final DiscussionCommentLikeRepository commentLikes;
  private final CircleRepository circles;
  private final UserRepository users;

  public DiscussionService(
      DiscussionRepository discussions,
      DiscussionThreadRepository threads,
      DiscussionCommentRepository comments,
      DiscussionThreadLikeRepository likes,
      DiscussionLikeRepository discussionLikes,
      DiscussionCommentLikeRepository commentLikes,
      CircleRepository circles,
      UserRepository users) {
    this.discussions = discussions;
    this.threads = threads;
    this.comments = comments;
    this.likes = likes;
    this.discussionLikes = discussionLikes;
    this.commentLikes = commentLikes;
    this.circles = circles;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public List<DiscussionDto> list(String query, String genre, String tab, UUID callerId) {
    List<Discussion> all =
        "my".equalsIgnoreCase(tab) && callerId != null
            ? discussions.findByCreatedByOrderByCreatedAtDesc(callerId)
            : discussions.findAllByOrderByCreatedAtDesc();

    String q = query == null ? "" : query.trim().toLowerCase();
    String g = genre == null ? "" : genre.trim().toLowerCase();
    List<Discussion> filtered = new ArrayList<>();
    for (Discussion d : all) {
      if (!g.isEmpty() && !g.equals("all") && (d.getGenre() == null || !d.getGenre().toLowerCase().contains(g))) {
        continue;
      }
      if (!q.isEmpty()) {
        String hay =
            ((d.getBookTitle() == null ? "" : d.getBookTitle()) + " "
                    + (d.getAuthor() == null ? "" : d.getAuthor()) + " "
                    + (d.getDiscussionTitle() == null ? "" : d.getDiscussionTitle()) + " "
                    + (d.getDiscussionExcerpt() == null ? "" : d.getDiscussionExcerpt()) + " "
                    + (d.getGenre() == null ? "" : d.getGenre()) + " "
                    + (d.getTag() == null ? "" : d.getTag()) + " "
                    + (d.getTags() == null ? "" : d.getTags()))
                .toLowerCase();
        if (!hay.contains(q)) {
          continue;
        }
      }
      filtered.add(d);
    }

    List<DiscussionDto> out = new ArrayList<>();
    for (Discussion d : filtered) {
      out.add(toDto(d, callerId, false));
    }
    if ("popular".equalsIgnoreCase(tab)) {
      out.sort(
          Comparator.comparingInt(
                  (DiscussionDto d) -> d.viewsCount() + d.repliesCount() * 5)
              .reversed());
    }
    return out;
  }

  @Transactional
  public DiscussionDto getById(UUID id, UUID callerId) {
    Discussion d = discussions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discussion not found"));
    d.setViewsCount(d.getViewsCount() + 1);
    discussions.save(d);
    return toDto(d, callerId, true);
  }

  @Transactional
  public DiscussionDto create(CreateDiscussionRequest req, UUID callerId) {
    String bookTitle = req.bookTitle().trim();
    String author = req.author().trim();
    if (bookTitle.isEmpty() || author.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book title and author are required");
    }
    Discussion d = new Discussion();
    d.setBookTitle(bookTitle);
    d.setAuthor(author);
    d.setGenre(req.genre() == null || req.genre().isBlank() ? "General" : req.genre().trim());
    d.setPageCount(req.pageCount());
    d.setPublicationYear(req.publicationYear());
    d.setCoverImageUrl(req.coverImageUrl());
    d.setKeyQuote(req.keyQuote());
    d.setQuoteAuthor(req.quoteAuthor() == null || req.quoteAuthor().isBlank() ? author : req.quoteAuthor().trim());
    d.setDiscussionTitle(
        req.discussionTitle() == null || req.discussionTitle().isBlank()
            ? bookTitle + " – Discussion & Analysis"
            : req.discussionTitle().trim());
    d.setDiscussionExcerpt(
        req.discussionExcerpt() == null || req.discussionExcerpt().isBlank()
            ? "Discussion started for " + bookTitle + ". Share your impressions and insights!"
            : req.discussionExcerpt().trim());
    // Multi-tag support: req.tags() (list) preferred, fallback to legacy req.tag().
    java.util.List<String> incomingTags = req.tags() == null ? java.util.List.of() : req.tags().stream()
        .filter(t -> t != null && !t.isBlank())
        .map(String::trim)
        .limit(10)
        .toList();
    if (!incomingTags.isEmpty()) {
      d.setTags(String.join(",", incomingTags));
      d.setTag(incomingTags.get(0));
    } else {
      d.setTag(req.tag() == null || req.tag().isBlank() ? d.getGenre() : req.tag().trim());
      d.setTags(d.getTag());
    }
    d.setCreatedBy(callerId);
    d.setViewsCount(0);
    if (req.circleId() != null && !req.circleId().isBlank()) {
      try {
        d.setCircleId(UUID.fromString(req.circleId().trim()));
      } catch (IllegalArgumentException ignored) {}
    }
    discussions.save(d);
    return toDto(d, callerId, true);
  }

  @Transactional
  public ThreadDto addThread(UUID discussionId, String title, String content, UUID callerId) {
    Discussion d =
        discussions
            .findById(discussionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discussion not found"));
    String body = content == null ? "" : content.trim();
    if (body.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contribution text is required");
    }
    DiscussionThread t =
        new DiscussionThread(
            d.getId(),
            title == null || title.isBlank() ? "Community Contribution" : title.trim(),
            body,
            callerId);
    threads.save(t);
    return toThreadDto(t, callerId);
  }

  @Transactional
  public CommentDto addComment(UUID threadId, String content, UUID callerId) {
    return addComment(threadId, content, callerId, null);
  }

  @Transactional
  public CommentDto addComment(UUID threadId, String content, UUID callerId, UUID parentCommentId) {
    DiscussionThread t =
        threads
            .findById(threadId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread not found"));
    String body = content == null ? "" : content.trim();
    if (body.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment text is required");
    }
    UUID parentId = null;
    String replyToAuthor = null;
    if (parentCommentId != null) {
      DiscussionComment parent =
          comments
              .findById(parentCommentId)
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent comment not found"));
      if (!parent.getThreadId().equals(t.getId())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent comment belongs to another thread");
      }
      parentId = parent.getId();
      User pu = users.findById(parent.getAuthorId()).orElse(null);
      replyToAuthor = pu == null ? "Member" : displayName(pu);
    }
    DiscussionComment c = new DiscussionComment(t.getId(), callerId, body, parentId);
    comments.save(c);
    User u = users.findById(callerId).orElse(null);
    String authorName = u == null ? "Member" : displayName(u);
    String avatar = u == null ? null : u.getProfilePictureUrl();
    String idStr = c.getId() == null ? UUID.randomUUID().toString() : c.getId().toString();
    return new CommentDto(
        idStr, authorName, avatar, body, timeAgo(c.getCreatedAt()),
        parentId == null ? null : parentId.toString(), replyToAuthor, 0, false, List.of());
  }

  @Transactional
  public CommentDto addQuickComment(UUID discussionId, String content, UUID callerId) {
    Discussion d =
        discussions
            .findById(discussionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discussion not found"));
    String body = content == null ? "" : content.trim();
    if (body.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment text is required");
    }
    List<DiscussionThread> existingThreads = threads.findByDiscussionIdOrderByCreatedAtAsc(d.getId());
    DiscussionThread targetThread;
    if (existingThreads.isEmpty()) {
      targetThread = threads.save(new DiscussionThread(d.getId(), "General Discussion", d.getDiscussionExcerpt(), callerId));
    } else {
      targetThread = existingThreads.get(0);
    }
    DiscussionComment c = new DiscussionComment(targetThread.getId(), callerId, body);
    comments.save(c);
    User cu = users.findById(callerId).orElse(null);
    return toCommentDto(c, cu == null ? "Member" : displayName(cu), cu == null ? null : cu.getProfilePictureUrl());
  }

  @Transactional
  public Map<String, Object> toggleLike(UUID threadId, UUID callerId) {
    DiscussionThread t =
        threads
            .findById(threadId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread not found"));
    var existing = likes.findByThreadIdAndUserId(threadId, callerId);
    boolean liked;
    if (existing.isPresent()) {
      likes.delete(existing.get());
      t.setLikesCount(Math.max(0, t.getLikesCount() - 1));
      liked = false;
    } else {
      likes.save(new DiscussionThreadLike(threadId, callerId));
      t.setLikesCount(t.getLikesCount() + 1);
      liked = true;
    }
    threads.save(t);
    return Map.of("liked", liked, "likesCount", t.getLikesCount());
  }

  @Transactional
  public Map<String, Object> toggleDiscussionLike(UUID discussionId, UUID callerId) {
    Discussion d =
        discussions
            .findById(discussionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discussion not found"));
    var existing = discussionLikes.findByDiscussionIdAndUserId(discussionId, callerId);
    boolean liked;
    if (existing.isPresent()) {
      discussionLikes.delete(existing.get());
      d.setLikesCount(Math.max(0, d.getLikesCount() - 1));
      liked = false;
    } else {
      discussionLikes.save(new DiscussionLike(discussionId, callerId));
      d.setLikesCount(d.getLikesCount() + 1);
      liked = true;
    }
    discussions.save(d);
    return Map.of("liked", liked, "likesCount", d.getLikesCount());
  }

  @Transactional
  public Map<String, Object> toggleCommentLike(UUID commentId, UUID callerId) {
    DiscussionComment c =
        comments
            .findById(commentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
    if (commentLikes == null) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Comment likes not available");
    }
    var existing = commentLikes.findByCommentIdAndUserId(commentId, callerId);
    boolean liked;
    if (existing.isPresent()) {
      commentLikes.delete(existing.get());
      c.setLikesCount(Math.max(0, c.getLikesCount() - 1));
      liked = false;
    } else {
      commentLikes.save(new DiscussionCommentLike(commentId, callerId));
      c.setLikesCount(c.getLikesCount() + 1);
      liked = true;
    }
    comments.save(c);
    return Map.of("liked", liked, "likesCount", c.getLikesCount());
  }

  private DiscussionDto toDto(Discussion d, UUID callerId, boolean includeThreads) {
    List<DiscussionThread> threadRows = threads.findByDiscussionIdOrderByCreatedAtAsc(d.getId());
    List<ThreadDto> threadDtos = new ArrayList<>();
    int commentTotal = 0;
    Set<UUID> participants = new LinkedHashSet<>();
    participants.add(d.getCreatedBy());
    for (DiscussionThread t : threadRows) {
      participants.add(t.getAuthorId());
      List<DiscussionComment> cc = comments.findByThreadIdOrderByCreatedAtAsc(t.getId());
      for (DiscussionComment c : cc) {
        participants.add(c.getAuthorId());
      }
      commentTotal += cc.size();
      if (includeThreads) {
        threadDtos.add(toThreadDto(t, cc, callerId));
      }
    }
    int repliesCount = threadRows.size() + commentTotal;
    User creator = users.findById(d.getCreatedBy()).orElse(null);
    String createdByName = creator == null ? "Member" : displayName(creator);

    String circleName = null;
    String circleIdStr = null;
    if (d.getCircleId() != null) {
      circleIdStr = d.getCircleId().toString();
      circleName = circles.findById(d.getCircleId()).map(Circle::getName).orElse(null);
    }
    boolean userLiked = callerId != null && discussionLikes.findByDiscussionIdAndUserId(d.getId(), callerId).isPresent();

    return new DiscussionDto(
        d.getId().toString(),
        d.getBookTitle(),
        d.getAuthor(),
        d.getGenre(),
        d.getPageCount(),
        d.getPublicationYear(),
        d.getCoverImageUrl() == null ? "" : d.getCoverImageUrl(),
        d.getKeyQuote(),
        d.getQuoteAuthor(),
        d.getDiscussionTitle(),
        d.getDiscussionExcerpt() == null ? "" : d.getDiscussionExcerpt(),
        d.getTag() == null ? d.getGenre() : d.getTag(),
        splitTags(d.getTags() != null ? d.getTags() : d.getTag()),
        "#faeae5",
        repliesCount,
        d.getViewsCount(),
        timeAgo(d.getCreatedAt()),
        d.getCreatedAt() == null ? null : d.getCreatedAt().toString(),
        createdByName,
        d.getCreatedBy().toString(),
        Math.max(1, participants.size()),
        List.of(),
        threadDtos,
        circleIdStr,
        circleName,
        d.getLikesCount(),
        userLiked);
  }

  private ThreadDto toThreadDto(DiscussionThread t, UUID callerId) {
    return toThreadDto(t, comments.findByThreadIdOrderByCreatedAtAsc(t.getId()), callerId);
  }

  private ThreadDto toThreadDto(DiscussionThread t, List<DiscussionComment> cc, UUID callerId) {
    User author = users.findById(t.getAuthorId()).orElse(null);
    List<CommentDto> out = buildCommentTree(cc, callerId);
    boolean userLiked = callerId != null && likes.findByThreadIdAndUserId(t.getId(), callerId).isPresent();
    return new ThreadDto(
        t.getId().toString(),
        t.getTitle(),
        author == null ? "Member" : displayName(author),
        author == null ? null : author.getProfilePictureUrl(),
        timeAgo(t.getCreatedAt()),
        false,
        t.getContent(),
        cc.size(),
        t.getLikesCount(),
        userLiked,
        out);
  }

  private List<CommentDto> buildCommentTree(List<DiscussionComment> cc, UUID callerId) {
    Map<String, CommentDto> byId = new java.util.LinkedHashMap<>();
    Map<String, String> parentOf = new java.util.LinkedHashMap<>();
    Map<String, List<CommentDto>> childrenOf = new java.util.LinkedHashMap<>();
    Map<String, String> authorById = new java.util.LinkedHashMap<>();
    for (DiscussionComment c : cc) {
      User cu = users.findById(c.getAuthorId()).orElse(null);
      String name = cu == null ? "Member" : displayName(cu);
      String avatar = cu == null ? null : cu.getProfilePictureUrl();
      String idStr = c.getId() == null ? UUID.randomUUID().toString() : c.getId().toString();
      authorById.put(idStr, name);
      boolean liked = callerId != null && commentLikes != null
          && commentLikes.findByCommentIdAndUserId(c.getId(), callerId).isPresent();
      CommentDto dto = new CommentDto(
          idStr, name, avatar, c.getContent(), timeAgo(c.getCreatedAt()),
          c.getParentCommentId() == null ? null : c.getParentCommentId().toString(),
          null, c.getLikesCount(), liked, new ArrayList<>());
      byId.put(idStr, dto);
      parentOf.put(idStr, c.getParentCommentId() == null ? null : c.getParentCommentId().toString());
      childrenOf.putIfAbsent(idStr, new ArrayList<>());
    }
    // second pass: resolve replyToAuthor + attach children
    List<CommentDto> roots = new ArrayList<>();
    for (Map.Entry<String, CommentDto> e : byId.entrySet()) {
      String id = e.getKey();
      String parentId = parentOf.get(id);
      String replyTo = parentId == null ? null : authorById.get(parentId);
      CommentDto cur = e.getValue();
      CommentDto withReplyTo = new CommentDto(
          cur.id(), cur.author(), cur.avatar(), cur.content(), cur.createdAt(),
          cur.parentId(), replyTo, cur.likesCount(), cur.userLiked(), childrenOf.get(id));
      byId.put(id, withReplyTo);
    }
    for (Map.Entry<String, CommentDto> e : byId.entrySet()) {
      String id = e.getKey();
      String parentId = parentOf.get(id);
      if (parentId != null && byId.containsKey(parentId)) {
        childrenOf.get(parentId).add(e.getValue());
      } else {
        roots.add(e.getValue());
      }
    }
    return roots;
  }

  private CommentDto toCommentDto(DiscussionComment c, String authorName, String avatarUrl) {
    String idStr = c.getId() == null ? UUID.randomUUID().toString() : c.getId().toString();
    return new CommentDto(
        idStr, authorName, avatarUrl, c.getContent(), timeAgo(c.getCreatedAt()),
        c.getParentCommentId() == null ? null : c.getParentCommentId().toString(),
        null, c.getLikesCount(), false, List.of());
  }

  private String displayName(User u) {
    if (u.getName() != null && !u.getName().isBlank()) return u.getName();
    if (u.getEmail() != null && !u.getEmail().isBlank()) {
      int at = u.getEmail().indexOf('@');
      return at > 0 ? u.getEmail().substring(0, at) : u.getEmail();
    }
    return "Member";
  }

  private static List<String> splitTags(String tags) {
    if (tags == null || tags.isBlank()) return List.of();
    List<String> out = new ArrayList<>();
    for (String part : tags.split("[,#]")) {
      String t = part.trim().replaceAll("^#+", "");
      if (!t.isEmpty()) out.add(t);
    }
    return out;
  }

  private String timeAgo(Instant i) {
    if (i == null) return "just now";
    long mins = Math.max(0, Duration.between(i, Instant.now()).toMinutes());
    if (mins < 1) return "just now";
    if (mins < 60) return mins + "m ago";
    long hrs = mins / 60;
    if (hrs < 24) return hrs + "h ago";
    long days = hrs / 24;
    return days + "d ago";
  }
}
