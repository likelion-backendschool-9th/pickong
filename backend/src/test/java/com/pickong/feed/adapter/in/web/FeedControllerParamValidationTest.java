package com.pickong.feed.adapter.in.web;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.LoginUser;
import com.pickong.feed.application.dto.FeedPage;
import com.pickong.feed.application.port.in.FeedUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @Validated 파라미터 검증(@Min, @Max, @Positive)은 Spring AOP가 필요하므로
// 전체 컨텍스트를 로드하는 @SpringBootTest로 테스트함 (Docker 필요)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FeedControllerParamValidationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private FeedUseCase feedUseCase;

    private MockMvc mockMvc;

    // 테스트용 인증 객체 — LoginUser(userId=1L)를 principal로 넣어 @CurrentUser 리졸버를 통과시킴
    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken(new LoginUser(1L), null, List.of());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void getFeeds_limit_0이하_400반환() throws Exception {
        // given: limit=0 → @Min(1) 위반 → ConstraintViolationException → 400
        mockMvc.perform(get("/feeds")
                        .with(authentication(AUTH))
                        .param("tab", "recommended")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void getFeeds_limit_51이상_400반환() throws Exception {
        // given: limit=51 → @Max(50) 위반 → ConstraintViolationException → 400
        mockMvc.perform(get("/feeds")
                        .with(authentication(AUTH))
                        .param("tab", "recommended")
                        .param("limit", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFeed_feedId_음수_400반환() throws Exception {
        // given: feedId=-1 → @Positive 위반 → ConstraintViolationException → 400
        // 단건 조회는 비로그인 허용이지만 Spring Security 패턴 매칭 이슈로 테스트에서는 auth 추가
        mockMvc.perform(get("/feeds/-1")
                        .with(authentication(AUTH)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFeeds_limit_정상범위_200반환() throws Exception {
        // given: limit=10 → 정상 → 200
        given(feedUseCase.getRecommendedFeeds(anyLong(), isNull(), anyInt()))
                .willReturn(new FeedPage(List.of(), null, false));

        mockMvc.perform(get("/feeds")
                        .with(authentication(AUTH))
                        .param("tab", "recommended")
                        .param("limit", "10"))
                .andExpect(status().isOk());
    }
}
