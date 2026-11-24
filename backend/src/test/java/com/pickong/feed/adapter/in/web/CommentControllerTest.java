package com.pickong.feed.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickong.common.error.BusinessException;
import com.pickong.common.error.GlobalExceptionHandler;
import com.pickong.common.security.LoginUser;
import com.pickong.common.web.CurrentUserArgumentResolver;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.application.dto.CommentPage;
import com.pickong.feed.application.port.in.CommentUseCase;
import com.pickong.feed.domain.model.Comment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
        // SecurityContextHolder에 인증 정보 주입 — @CurrentUser가 여기서 꺼냄
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(1L), null, List.of()));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommentController(commentUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        // 테스트 간 SecurityContext 오염 방지
        SecurityContextHolder.clearContext();
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
        given(commentUseCase.getComments(anyLong(), isNull(), anyInt()))
                .willReturn(new CommentPage(List.of(sampleComment()), null, false));

        mockMvc.perform(get("/feeds/10/comments").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments").isArray())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void getReplies_대댓글목록_200반환() throws Exception {
        given(commentUseCase.getReplies(anyLong(), anyLong(), isNull(), anyInt()))
                .willReturn(new CommentPage(List.of(sampleComment()), null, false));

        mockMvc.perform(get("/feeds/10/comments/1/replies").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments").isArray());
    }

    @Test
    void createComment_유효한요청_201반환() throws Exception {
        given(commentUseCase.createComment(anyLong(), any())).willReturn(sampleComment());
        // userId는 JWT(@CurrentUser)에서 꺼내므로 요청 본문에 없음
        Map<String, Object> body = Map.of("content", "테스트 댓글");

        mockMvc.perform(post("/feeds/10/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("테스트 댓글"));
    }

    @Test
    void createComment_내용누락_400반환() throws Exception {
        // content 없이 요청 → @NotBlank 위반
        Map<String, Object> body = Map.of();

        mockMvc.perform(post("/feeds/10/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateComment_유효한요청_200반환() throws Exception {
        given(commentUseCase.updateComment(anyLong(), anyLong(), anyLong(), any())).willReturn(sampleComment());
        Map<String, Object> body = Map.of("content", "수정된 댓글");

        mockMvc.perform(put("/feeds/10/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void updateComment_내용누락_400반환() throws Exception {
        // content 없이 수정 요청 → @NotBlank 위반
        Map<String, Object> body = Map.of();

        mockMvc.perform(put("/feeds/10/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateComment_존재하지않는id_404반환() throws Exception {
        given(commentUseCase.updateComment(anyLong(), anyLong(), anyLong(), any()))
                .willThrow(new BusinessException(FeedErrorCode.COMMENT_NOT_FOUND));
        Map<String, Object> body = Map.of("content", "수정된 댓글");

        mockMvc.perform(put("/feeds/10/comments/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateComment_타인댓글_수정시_403반환() throws Exception {
        given(commentUseCase.updateComment(anyLong(), anyLong(), anyLong(), any()))
                .willThrow(new BusinessException(FeedErrorCode.COMMENT_NOT_OWNER));
        Map<String, Object> body = Map.of("content", "수정된 댓글");

        mockMvc.perform(put("/feeds/10/comments/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void deleteComment_성공_204반환() throws Exception {
        mockMvc.perform(delete("/feeds/10/comments/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteComment_존재하지않는id_404반환() throws Exception {
        willThrow(new BusinessException(FeedErrorCode.COMMENT_NOT_FOUND))
                .given(commentUseCase).deleteComment(anyLong(), anyLong(), anyLong());

        mockMvc.perform(delete("/feeds/10/comments/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteComment_타인댓글_삭제시_403반환() throws Exception {
        willThrow(new BusinessException(FeedErrorCode.COMMENT_NOT_OWNER))
                .given(commentUseCase).deleteComment(anyLong(), anyLong(), anyLong());

        mockMvc.perform(delete("/feeds/10/comments/2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}
