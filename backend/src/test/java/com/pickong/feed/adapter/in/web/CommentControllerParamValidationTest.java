package com.pickong.feed.adapter.in.web;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.LoginUser;
import com.pickong.feed.application.port.in.CommentUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
 * feedId · commentId @Positive 검증 및 비로그인 접근 테스트.
 * standaloneSetup은 Spring AOP · Security를 적용하지 않으므로 전체 컨텍스트를 띄움
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CommentControllerParamValidationTest {

    @MockitoBean
    private CommentUseCase commentUseCase;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    // 댓글 작성·수정·삭제는 로그인 필수 — 테스트용 인증 객체
    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken(new LoginUser(1L), null, List.of());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ─── @Positive 파라미터 검증 ─────────────────────────────

    @Test
    void createComment_feedId가0이하면_400반환() throws Exception {
        mockMvc.perform(post("/feeds/-1/comments")
                        .with(authentication(AUTH)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateComment_feedId가0이하면_400반환() throws Exception {
        mockMvc.perform(put("/feeds/-1/comments/1")
                        .with(authentication(AUTH)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateComment_commentId가0이하면_400반환() throws Exception {
        mockMvc.perform(put("/feeds/1/comments/-1")
                        .with(authentication(AUTH)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteComment_feedId가0이하면_400반환() throws Exception {
        mockMvc.perform(delete("/feeds/-1/comments/1")
                        .with(authentication(AUTH)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteComment_commentId가0이하면_400반환() throws Exception {
        mockMvc.perform(delete("/feeds/1/comments/-1")
                        .with(authentication(AUTH)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // ─── 비로그인 접근 → 401 ──────────────────────────────────

    @Test
    void createComment_비로그인_401반환() throws Exception {
        mockMvc.perform(post("/feeds/1/comments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateComment_비로그인_401반환() throws Exception {
        mockMvc.perform(put("/feeds/1/comments/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteComment_비로그인_401반환() throws Exception {
        mockMvc.perform(delete("/feeds/1/comments/1").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
