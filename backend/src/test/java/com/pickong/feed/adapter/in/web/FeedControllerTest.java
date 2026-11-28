package com.pickong.feed.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickong.common.domain.Visibility;
import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.common.error.GlobalExceptionHandler;
import com.pickong.common.security.LoginUser;
import com.pickong.common.web.CurrentUserArgumentResolver;
import com.pickong.feed.application.port.in.FeedUseCase;
import com.pickong.feed.application.port.in.FeedViewUseCase;
import com.pickong.feed.application.dto.FeedAuthor;
import com.pickong.feed.application.dto.FeedView;
import com.pickong.feed.domain.model.Feed;
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

    // 화면용 정보는 조회 SQL 없이 빈 값으로 붙임 (작성자 SQL은 FeedViewQueryAdapterIntegrationTest에서 확인)
    private final FeedViewUseCase feedViewUseCase = (feeds, viewerId) -> feeds.stream()
            .map(feed -> new FeedView(feed, new FeedAuthor("author-public-id", "writer", null, null, "서울"),
                    List.of("https://cdn.example/a.jpg"), List.of(), false, false))
            .toList();

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // SecurityContextHolder에 인증 정보 주입 — @CurrentUser가 여기서 꺼냄
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(1L), null, List.of()));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new FeedController(feedUseCase, feedViewUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        // 테스트 간 SecurityContext 오염 방지
        SecurityContextHolder.clearContext();
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
        // given: userId는 SecurityContext에서 꺼내므로 본문에 포함하지 않음
        given(feedUseCase.createFeed(any())).willReturn(sampleFeed());
        Map<String, Object> body = Map.of(
                "content", "테스트 게시물",
                "visibility", "PUBLIC"
        );

        // when & then
        mockMvc.perform(post("/feeds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.content").value("테스트 게시물"))
                // PostDto 모양: 작성자는 공개 id로, 내부 순번 userId는 내보내지 않음
                .andExpect(jsonPath("$.author.id").value("author-public-id"))
                .andExpect(jsonPath("$.author.username").value("writer"))
                .andExpect(jsonPath("$.author.nickname").value("writer"))
                .andExpect(jsonPath("$.photos[0].url").value("https://cdn.example/a.jpg"))
                .andExpect(jsonPath("$.isLiked").value(false))
                .andExpect(jsonPath("$.isBookmarked").value(false))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.publicId").doesNotExist());
    }

    @Test
    void createFeed_내용누락_400반환() throws Exception {
        // given: content 없이 요청 → @NotBlank 위반
        Map<String, Object> body = Map.of("visibility", "PUBLIC");

        // when & then
        mockMvc.perform(post("/feeds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFeed_공개범위누락_400반환() throws Exception {
        // given: visibility 없이 요청 → @NotNull 위반
        Map<String, Object> body = Map.of("content", "테스트 게시물");

        // when & then
        mockMvc.perform(post("/feeds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFeed_단건조회_200반환() throws Exception {
        // given
        given(feedUseCase.getFeed(1L, 1L, false)).willReturn(sampleFeed());

        // when & then
        mockMvc.perform(get("/feeds/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void getFeed_존재하지않는id_404반환() throws Exception {
        // given: 없는 id → BusinessException → GlobalExceptionHandler가 404로 변환
        given(feedUseCase.getFeed(999L, 1L, false))
                .willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/feeds/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateFeed_유효한요청_200반환() throws Exception {
        // given
        given(feedUseCase.updateFeed(anyLong(), anyLong(), any())).willReturn(sampleFeed());
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
    void updateFeed_타인피드_수정시_403반환() throws Exception {
        // given
        given(feedUseCase.updateFeed(anyLong(), anyLong(), any()))
                .willThrow(new BusinessException(FeedErrorCode.FEED_NOT_OWNER));
        Map<String, Object> body = Map.of("content", "수정된 내용", "visibility", "PUBLIC");

        // when & then
        mockMvc.perform(put("/feeds/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void deleteFeed_성공_204반환() throws Exception {
        // when & then: 정상 삭제 → 204 No Content (댓글 삭제와 같게)
        mockMvc.perform(delete("/feeds/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteFeed_존재하지않는id_404반환() throws Exception {
        // given
        willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND))
                .given(feedUseCase).deleteFeed(anyLong(), anyLong());

        // when & then
        mockMvc.perform(delete("/feeds/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteFeed_타인피드_삭제시_403반환() throws Exception {
        // given
        willThrow(new BusinessException(FeedErrorCode.FEED_NOT_OWNER))
                .given(feedUseCase).deleteFeed(anyLong(), anyLong());

        // when & then
        mockMvc.perform(delete("/feeds/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}
