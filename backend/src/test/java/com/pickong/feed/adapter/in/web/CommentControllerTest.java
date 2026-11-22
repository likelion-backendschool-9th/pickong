package com.pickong.feed.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickong.common.error.BusinessException;
import com.pickong.common.error.ErrorCode;
import com.pickong.common.error.GlobalExceptionHandler;
import com.pickong.feed.application.dto.CommentPage;
import com.pickong.feed.application.port.in.CommentUseCase;
import com.pickong.feed.domain.model.Comment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    @Mock
    private CommentUseCase commentUseCase;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommentController(commentUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Comment sampleComment() {
        return Comment.builder()
                .id(1L)
                .feedId(10L)
                .userId(1L)
                .content("테스트 댓글")
                .likeCount(0)
                .build();
    }

    @Test
    void getComments_목록조회_200반환() throws Exception {
        // given
        given(commentUseCase.getComments(anyLong(), isNull(), anyInt()))
                .willReturn(new CommentPage(List.of(sampleComment()), null, false));

        // when & then
        mockMvc.perform(get("/feeds/10/comments").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments").isArray())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void getReplies_대댓글목록_200반환() throws Exception {
        // given
        given(commentUseCase.getReplies(anyLong(), anyLong(), isNull(), anyInt()))
                .willReturn(new CommentPage(List.of(sampleComment()), null, false));

        // when & then
        mockMvc.perform(get("/feeds/10/comments/1/replies").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments").isArray());
    }

    @Test
    void createComment_유효한요청_200반환() throws Exception {
        // given
        given(commentUseCase.createComment(anyLong(), any())).willReturn(sampleComment());
        Map<String, Object> body = Map.of("userId", 1, "content", "테스트 댓글");

        // when & then
        mockMvc.perform(post("/feeds/10/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("테스트 댓글"));
    }

    @Test
    void createComment_내용누락_400반환() throws Exception {
        // given: content 없이 요청 → @NotBlank 위반
        Map<String, Object> body = Map.of("userId", 1);

        // when & then
        mockMvc.perform(post("/feeds/10/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateComment_유효한요청_200반환() throws Exception {
        // given
        given(commentUseCase.updateComment(anyLong(), any())).willReturn(sampleComment());
        Map<String, Object> body = Map.of("content", "수정된 댓글");

        // when & then
        mockMvc.perform(put("/feeds/10/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void updateComment_내용누락_400반환() throws Exception {
        // given: content 없이 수정 요청 → @NotBlank 위반
        Map<String, Object> body = Map.of();

        // when & then
        mockMvc.perform(put("/feeds/10/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateComment_존재하지않는id_404반환() throws Exception {
        // given
        given(commentUseCase.updateComment(anyLong(), any()))
                .willThrow(new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        Map<String, Object> body = Map.of("content", "수정된 댓글");

        // when & then
        mockMvc.perform(put("/feeds/10/comments/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteComment_성공_200반환() throws Exception {
        mockMvc.perform(delete("/feeds/10/comments/1"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteComment_존재하지않는id_404반환() throws Exception {
        // given
        willThrow(new BusinessException(ErrorCode.COMMENT_NOT_FOUND))
                .given(commentUseCase).deleteComment(999L);

        // when & then
        mockMvc.perform(delete("/feeds/10/comments/999"))
                .andExpect(status().isNotFound());
    }
}
