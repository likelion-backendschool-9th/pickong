package com.pickong.feed.adapter.in.web;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.ErrorCode;
import com.pickong.common.error.GlobalExceptionHandler;
import com.pickong.feed.application.port.in.FeedReactionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Spring 컨텍스트 없이 MockMvc로 웹 계층(응답 코드·예외 처리)만 테스트
// 참고: @Positive 파라미터 검증은 AOP가 필요해서 별도 @SpringBootTest 클래스에서 테스트함
@ExtendWith(MockitoExtension.class)
class ReactionControllerTest {

    @Mock
    private FeedReactionUseCase feedReactionUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 컨트롤러 + 전역 예외 처리기를 조합해 MockMvc를 직접 구성
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReactionController(feedReactionUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─────────────────────────────────────────────
    //  POST /feeds/{feedId}/likes
    // ─────────────────────────────────────────────

    @Test
    void likeFeed_성공_201반환() throws Exception {
        // when & then: 정상 호출 → 201 Created
        mockMvc.perform(post("/feeds/1/likes").param("userId", "1"))
                .andExpect(status().isCreated());
    }

    @Test
    void likeFeed_피드없음_404반환() throws Exception {
        // given: 없는 피드 → BusinessException(FEED_NOT_FOUND) → GlobalExceptionHandler → 404
        willThrow(new BusinessException(ErrorCode.FEED_NOT_FOUND))
                .given(feedReactionUseCase).likeFeed(999L, 1L);

        // when & then
        mockMvc.perform(post("/feeds/999/likes").param("userId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void likeFeed_이미좋아요_409반환() throws Exception {
        // given: 이미 좋아요 → ALREADY_LIKED → 409
        willThrow(new BusinessException(ErrorCode.ALREADY_LIKED))
                .given(feedReactionUseCase).likeFeed(1L, 1L);

        // when & then
        mockMvc.perform(post("/feeds/1/likes").param("userId", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ─────────────────────────────────────────────
    //  DELETE /feeds/{feedId}/likes
    // ─────────────────────────────────────────────

    @Test
    void unlikeFeed_성공_204반환() throws Exception {
        // when & then: 정상 호출 → 204 No Content
        mockMvc.perform(delete("/feeds/1/likes").param("userId", "1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void unlikeFeed_좋아요없음_404반환() throws Exception {
        // given: 좋아요 없는 상태에서 취소 → LIKE_NOT_FOUND → 404
        willThrow(new BusinessException(ErrorCode.LIKE_NOT_FOUND))
                .given(feedReactionUseCase).unlikeFeed(1L, 1L);

        // when & then
        mockMvc.perform(delete("/feeds/1/likes").param("userId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ─────────────────────────────────────────────
    //  POST /feeds/{feedId}/bookmarks
    // ─────────────────────────────────────────────

    @Test
    void bookmarkFeed_성공_201반환() throws Exception {
        // when & then: 정상 호출 → 201 Created
        mockMvc.perform(post("/feeds/1/bookmarks").param("userId", "1"))
                .andExpect(status().isCreated());
    }

    @Test
    void bookmarkFeed_피드없음_404반환() throws Exception {
        // given: 없는 피드 → FEED_NOT_FOUND → 404
        willThrow(new BusinessException(ErrorCode.FEED_NOT_FOUND))
                .given(feedReactionUseCase).bookmarkFeed(999L, 1L);

        // when & then
        mockMvc.perform(post("/feeds/999/bookmarks").param("userId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void bookmarkFeed_이미북마크_409반환() throws Exception {
        // given: 이미 북마크 → ALREADY_BOOKMARKED → 409
        willThrow(new BusinessException(ErrorCode.ALREADY_BOOKMARKED))
                .given(feedReactionUseCase).bookmarkFeed(1L, 1L);

        // when & then
        mockMvc.perform(post("/feeds/1/bookmarks").param("userId", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ─────────────────────────────────────────────
    //  DELETE /feeds/{feedId}/bookmarks
    // ─────────────────────────────────────────────

    @Test
    void unbookmarkFeed_성공_204반환() throws Exception {
        // when & then: 정상 호출 → 204 No Content
        mockMvc.perform(delete("/feeds/1/bookmarks").param("userId", "1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void unbookmarkFeed_북마크없음_404반환() throws Exception {
        // given: 북마크 없는 상태에서 취소 → BOOKMARK_NOT_FOUND → 404
        willThrow(new BusinessException(ErrorCode.BOOKMARK_NOT_FOUND))
                .given(feedReactionUseCase).unbookmarkFeed(1L, 1L);

        // when & then
        mockMvc.perform(delete("/feeds/1/bookmarks").param("userId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
