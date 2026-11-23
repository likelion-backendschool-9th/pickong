package com.pickong.feed.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickong.common.error.BusinessException;
import com.pickong.common.error.ErrorCode;
import com.pickong.common.error.GlobalExceptionHandler;
import com.pickong.feed.application.port.in.CommentReactionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CommentReactionControllerTest {

    @Mock
    private CommentReactionUseCase commentReactionUseCase;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommentReactionController(commentReactionUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void likeComment_성공_201반환() throws Exception {
        Map<String, Object> body = Map.of("userId", 2);

        mockMvc.perform(post("/feeds/10/comments/1/likes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void likeComment_userId누락_400반환() throws Exception {
        // userId 없이 요청 → @NotNull 위반
        mockMvc.perform(post("/feeds/10/comments/1/likes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void likeComment_이미좋아요_409반환() throws Exception {
        willThrow(new BusinessException(ErrorCode.ALREADY_COMMENT_LIKED))
                .given(commentReactionUseCase).likeComment(anyLong(), anyLong(), anyLong());
        Map<String, Object> body = Map.of("userId", 2);

        mockMvc.perform(post("/feeds/10/comments/1/likes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void likeComment_존재하지않는피드_404반환() throws Exception {
        willThrow(new BusinessException(ErrorCode.FEED_NOT_FOUND))
                .given(commentReactionUseCase).likeComment(anyLong(), anyLong(), anyLong());
        Map<String, Object> body = Map.of("userId", 2);

        mockMvc.perform(post("/feeds/999/comments/1/likes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unlikeComment_성공_204반환() throws Exception {
        mockMvc.perform(delete("/feeds/10/comments/1/likes")
                        .param("userId", "2"))
                .andExpect(status().isNoContent());
    }

    @Test
    void unlikeComment_좋아요없음_404반환() throws Exception {
        willThrow(new BusinessException(ErrorCode.COMMENT_LIKE_NOT_FOUND))
                .given(commentReactionUseCase).unlikeComment(anyLong(), anyLong(), anyLong());

        mockMvc.perform(delete("/feeds/10/comments/1/likes")
                        .param("userId", "2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
