package com.readerscircle.discussions;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.readerscircle.discussions.dto.CommentDto;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DiscussionControllerTest {

  @Mock private DiscussionService service;
  @InjectMocks private DiscussionController controller;

  private MockMvc mockMvc;
  private UUID userId;
  private UsernamePasswordAuthenticationToken auth;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    userId = UUID.randomUUID();
    auth = new UsernamePasswordAuthenticationToken(userId.toString(), "pw", java.util.List.of());
  }

  @Test
  void toggleDiscussionLikeReturnsUpdatedStatus() throws Exception {
    UUID discussionId = UUID.randomUUID();
    when(service.toggleDiscussionLike(eq(discussionId), eq(userId)))
        .thenReturn(Map.of("liked", true, "likesCount", 25));

    mockMvc
        .perform(post("/api/discussions/" + discussionId + "/like").principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.liked").value(true))
        .andExpect(jsonPath("$.likesCount").value(25));
  }

  @Test
  void addQuickCommentReturnsCreatedComment() throws Exception {
    UUID discussionId = UUID.randomUUID();
    when(service.addQuickComment(eq(discussionId), eq("Love this book!"), eq(userId)))
        .thenReturn(new CommentDto(UUID.randomUUID().toString(), "Sharan", null, "Love this book!", "just now"));

    mockMvc
        .perform(
            post("/api/discussions/" + discussionId + "/comments")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Love this book!\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.content").value("Love this book!"))
        .andExpect(jsonPath("$.author").value("Sharan"));
  }
}
