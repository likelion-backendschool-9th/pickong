package com.pickong.feed.adapter.in.web;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.LoginUser;
import com.pickong.feed.application.port.in.FeedReactionUseCase;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * feedId에 대한 @Positive 파라미터 검증 테스트.
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

    // 반응 API는 로그인 필수 — 테스트용 인증 객체
    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken(new LoginUser(1L), null, List.of());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void likeFeed_feedId가0이하면_400반환() throws Exception {
        // feedId에 @Positive 위반 (-1) → 400 Bad Request
        mockMvc.perform(post("/feeds/-1/likes").with(authentication(AUTH)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unlikeFeed_feedId가0이하면_400반환() throws Exception {
        mockMvc.perform(delete("/feeds/-1/likes").with(authentication(AUTH)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookmarkFeed_feedId가0이하면_400반환() throws Exception {
        mockMvc.perform(post("/feeds/-1/bookmarks").with(authentication(AUTH)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unbookmarkFeed_feedId가0이하면_400반환() throws Exception {
        mockMvc.perform(delete("/feeds/-1/bookmarks").with(authentication(AUTH)).with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
