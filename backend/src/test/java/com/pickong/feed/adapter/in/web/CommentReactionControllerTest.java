package com.pickong.feed.adapter.in.web;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.GlobalExceptionHandler;
import com.pickong.common.security.LoginUser;
import com.pickong.common.web.CurrentUserArgumentResolver;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.application.port.in.CommentReactionUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CommentReactionControllerTest {

    @Mock
    private CommentReactionUseCase commentReactionUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // SecurityContextHolder에 인증 정보 주입 — CurrentUserArgumentResolver가 여기서 꺼냄
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(1L), null, List.of()));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommentReactionController(commentReactionUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        // 테스트 간 SecurityContext 오염 방지
        SecurityContextHolder.clearContext();
    }

    @Test
    void likeComment_성공_201반환() throws Exception {
        mockMvc.perform(post("/feeds/10/comments/1/likes"))
                .andExpect(status().isCreated());
    }

    @Test
    void likeComment_이미좋아요_409반환() throws Exception {
        willThrow(new BusinessException(FeedErrorCode.ALREADY_COMMENT_LIKED))
                .given(commentReactionUseCase).likeComment(anyLong(), anyLong(), anyLong());

        mockMvc.perform(post("/feeds/10/comments/1/likes"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void likeComment_존재하지않는피드_404반환() throws Exception {
        willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND))
                .given(commentReactionUseCase).likeComment(anyLong(), anyLong(), anyLong());

        mockMvc.perform(post("/feeds/999/comments/1/likes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unlikeComment_성공_204반환() throws Exception {
        mockMvc.perform(delete("/feeds/10/comments/1/likes"))
                .andExpect(status().isNoContent());
    }

    @Test
    void unlikeComment_좋아요없음_404반환() throws Exception {
        willThrow(new BusinessException(FeedErrorCode.COMMENT_LIKE_NOT_FOUND))
                .given(commentReactionUseCase).unlikeComment(anyLong(), anyLong(), anyLong());

        mockMvc.perform(delete("/feeds/10/comments/1/likes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
