package com.readerscircle.discussions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.discussions.dto.CommentDto;
import com.readerscircle.discussions.dto.CreateDiscussionRequest;
import com.readerscircle.discussions.dto.DiscussionDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscussionServiceTest {

  @Mock private DiscussionRepository discussions;
  @Mock private DiscussionThreadRepository threads;
  @Mock private DiscussionCommentRepository comments;
  @Mock private DiscussionThreadLikeRepository threadLikes;
  @Mock private DiscussionLikeRepository discussionLikes;
  @Mock private DiscussionCommentLikeRepository commentLikes;
  @Mock private CircleRepository circles;
  @Mock private UserRepository users;

  private DiscussionService service;

  @BeforeEach
  void setUp() {
    service =
        new DiscussionService(
            discussions, threads, comments, threadLikes, discussionLikes, commentLikes, circles, users);
  }

  @Test
  void createDiscussionStoresCircleIdAndPopulatesDto() {
    UUID callerId = UUID.randomUUID();
    UUID circleId = UUID.randomUUID();
    Circle circle = new Circle("Tamil Literature Circle", "Desc", "Chennai", callerId);
    when(circles.findById(circleId)).thenReturn(Optional.of(circle));
    when(discussions.save(any(Discussion.class))).thenAnswer(inv -> {
      Discussion d = inv.getArgument(0);
      d.setId(UUID.randomUUID());
      return d;
    });

    CreateDiscussionRequest req =
        new CreateDiscussionRequest(
            "Kannagi",
            "mannar mannan",
            "Tamil Literature",
            300,
            2020,
            "https://example.com/cover.jpg",
            "Quote",
            "Author",
            "Kannagi – Justice, Society and Feminine Strength",
            "Excerpt text",
            "#Kannagi #TamilLiterature",
            circleId.toString());

    DiscussionDto dto = service.create(req, callerId);

    assertThat(dto.bookTitle()).isEqualTo("Kannagi");
    assertThat(dto.circleId()).isEqualTo(circleId.toString());
    assertThat(dto.circleName()).isEqualTo("Tamil Literature Circle");
  }

  @Test
  void toggleDiscussionLikeTogglesStateAndCount() {
    UUID callerId = UUID.randomUUID();
    UUID discussionId = UUID.randomUUID();
    Discussion d = new Discussion();
    d.setId(discussionId);
    d.setLikesCount(24);
    when(discussions.findById(discussionId)).thenReturn(Optional.of(d));
    when(discussionLikes.findByDiscussionIdAndUserId(discussionId, callerId))
        .thenReturn(Optional.empty());

    Map<String, Object> result = service.toggleDiscussionLike(discussionId, callerId);

    assertThat(result.get("liked")).isEqualTo(true);
    assertThat(result.get("likesCount")).isEqualTo(25);
    verify(discussionLikes).save(any(DiscussionLike.class));
  }

  @Test
  void addQuickCommentCreatesCommentOnDiscussionThread() {
    UUID callerId = UUID.randomUUID();
    UUID discussionId = UUID.randomUUID();
    Discussion d = new Discussion();
    d.setId(discussionId);
    d.setDiscussionExcerpt("Book excerpt");
    when(discussions.findById(discussionId)).thenReturn(Optional.of(d));
    when(threads.findByDiscussionIdOrderByCreatedAtAsc(discussionId)).thenReturn(List.of());
    when(threads.save(any(DiscussionThread.class))).thenAnswer(inv -> inv.getArgument(0));
    when(users.findById(callerId)).thenReturn(Optional.of(new User("Sharan", "sharan@example.com", "pw", com.readerscircle.auth.Role.READER)));

    CommentDto comment = service.addQuickComment(discussionId, "Brilliant book analysis!", callerId);

    assertThat(comment.content()).isEqualTo("Brilliant book analysis!");
    assertThat(comment.author()).isEqualTo("Sharan");
    verify(comments).save(any(DiscussionComment.class));
  }
}
