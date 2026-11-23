package com.pickong.feed.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickong.common.domain.Visibility;
import com.pickong.common.error.BusinessException;
import com.pickong.common.error.ErrorCode;
import com.pickong.common.error.GlobalExceptionHandler;
import com.pickong.feed.application.port.in.FeedUseCase;
import com.pickong.feed.domain.model.Feed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Spring 컨텍스트 없이 MockMvc로 웹 계층만 테스트함
@ExtendWith(MockitoExtension.class)
class FeedControllerTest {

    @Mock
    private FeedUseCase feedUseCase;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // 컨트롤러 + 전역 예외 처리기를 조합해 MockMvc를 직접 구성
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FeedController(feedUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // 테스트용 샘플 Feed 객체 생성 헬퍼
    private Feed sampleFeed() {
        return Feed.builder()
                .id(1L)
                .userId(1L)
                .content("테스트 게시물")
                .visibility(Visibility.PUBLIC)
                .build();
    }

    @Test
    void createFeed_유효한요청_200반환() throws Exception {
        // given
        given(feedUseCase.createFeed(any())).willReturn(sampleFeed());
        Map<String, Object> body = Map.of(
                "userId", 1,
                "content", "테스트 게시물",
                "visibility", "PUBLIC"
        );

        // when & then
        mockMvc.perform(post("/feeds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("테스트 게시물"));
    }

    @Test
    void createFeed_내용누락_400반환() throws Exception {
        // given: content 없이 요청 → @NotBlank 위반
        Map<String, Object> body = Map.of(
                "userId", 1,
                "visibility", "PUBLIC"
        );

        // when & then
        mockMvc.perform(post("/feeds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFeed_공개범위누락_400반환() throws Exception {
        // given: visibility 없이 요청 → @NotNull 위반
        Map<String, Object> body = Map.of(
                "userId", 1,
                "content", "테스트 게시물"
        );

        // when & then
        mockMvc.perform(post("/feeds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFeed_단건조회_200반환() throws Exception {
        // given
        given(feedUseCase.getFeed(1L)).willReturn(sampleFeed());

        // when & then
        mockMvc.perform(get("/feeds/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getFeed_존재하지않는id_404반환() throws Exception {
        // given: 없는 id → BusinessException → GlobalExceptionHandler가 404로 변환
        given(feedUseCase.getFeed(999L))
                .willThrow(new BusinessException(ErrorCode.FEED_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/feeds/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateFeed_유효한요청_200반환() throws Exception {
        // given
        given(feedUseCase.updateFeed(anyLong(), any())).willReturn(sampleFeed());
        Map<String, Object> body = Map.of(
                "content", "수정된 내용",
                "visibility", "PUBLIC"
        );

        // when & then
        mockMvc.perform(put("/feeds/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void updateFeed_공개범위누락_400반환() throws Exception {
        // given: visibility 없이 수정 요청 → @NotNull 위반
        Map<String, Object> body = Map.of("content", "수정된 내용");

        // when & then
        mockMvc.perform(put("/feeds/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteFeed_성공_200반환() throws Exception {
        // when & then: 정상 삭제 → 200
        mockMvc.perform(delete("/feeds/1"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteFeed_존재하지않는id_404반환() throws Exception {
        // given
        willThrow(new BusinessException(ErrorCode.FEED_NOT_FOUND))
                .given(feedUseCase).deleteFeed(999L);

        // when & then
        mockMvc.perform(delete("/feeds/999"))
                .andExpect(status().isNotFound());
    }
}
