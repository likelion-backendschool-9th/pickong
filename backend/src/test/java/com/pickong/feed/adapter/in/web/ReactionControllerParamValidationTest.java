package com.pickong.feed.adapter.in.web;

import com.pickong.TestcontainersConfiguration;
import com.pickong.feed.application.port.in.FeedReactionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Positive 파라미터 검증 테스트.
 * standaloneSetup은 Spring AOP를 적용하지 않아 @Positive가 동작하지 않으므로
 * 전체 Spring 컨텍스트를 띄워서 검증함
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReactionControllerParamValidationTest {

    @MockitoBean
    private FeedReactionUseCase feedReactionUseCase;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void likeFeed_feedId가0이하면_400반환() throws Exception {
        // feedId에 @Positive 위반 (-1) → 400 Bad Request
        mockMvc.perform(post("/feeds/-1/likes").param("userId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void likeFeed_userId가0이하면_400반환() throws Exception {
        // userId에 @Positive 위반 (-1) → 400 Bad Request
        mockMvc.perform(post("/feeds/1/likes").param("userId", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void likeFeed_유효한요청_201반환() throws Exception {
        // 정상 요청 — feedId=1, userId=1
        mockMvc.perform(post("/feeds/1/likes").param("userId", "1"))
                .andExpect(status().isCreated());
    }

    @Test
    void unlikeFeed_feedId가0이하면_400반환() throws Exception {
        mockMvc.perform(delete("/feeds/-1/likes").param("userId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookmarkFeed_feedId가0이하면_400반환() throws Exception {
        mockMvc.perform(post("/feeds/-1/bookmarks").param("userId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unbookmarkFeed_userId가0이하면_400반환() throws Exception {
        mockMvc.perform(delete("/feeds/1/bookmarks").param("userId", "0"))
                .andExpect(status().isBadRequest());
    }
}
