package com.pickong.social.adapter.in.web;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.GlobalExceptionHandler;
import com.pickong.common.security.LoginUser;
import com.pickong.common.web.CurrentUserArgumentResolver;
import com.pickong.social.application.port.in.RelationCommandUseCase;
import com.pickong.social.domain.SocialErrorCode;
import com.pickong.user.domain.UserErrorCode;
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

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SocialControllerTest {

    private static final long LOGIN_USER_ID = 1L;

    @Mock
    private RelationCommandUseCase relationCommandUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // SecurityContextHolder에 로그인 사용자 주입 — CurrentUserArgumentResolver가 여기서 꺼냄
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(LOGIN_USER_ID), null, List.of()));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new SocialController(relationCommandUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        // 테스트 간 SecurityContext 오염 방지
        SecurityContextHolder.clearContext();
    }

    // ── POST /users/{username}/follow ──────────────────────────

    @Test
    void follow_성공_201반환_로그인사용자로팔로우() throws Exception {
        mockMvc.perform(post("/users/targetUser/follow"))
                .andExpect(status().isCreated());

        then(relationCommandUseCase).should().follow(LOGIN_USER_ID, "targetUser");
    }

    @Test
    void follow_본문의userId는무시_로그인사용자로팔로우() throws Exception {
        // 예전 방식처럼 남의 id를 본문에 넣어도 로그인 사용자로만 팔로우해야 됨
        mockMvc.perform(post("/users/targetUser/follow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 999}"))
                .andExpect(status().isCreated());

        then(relationCommandUseCase).should().follow(LOGIN_USER_ID, "targetUser");
        then(relationCommandUseCase).shouldHaveNoMoreInteractions();
    }

    @Test
    void follow_존재하지않는사용자_404반환() throws Exception {
        willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND))
                .given(relationCommandUseCase).follow(LOGIN_USER_ID, "nobody");

        mockMvc.perform(post("/users/nobody/follow"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void follow_자기자신팔로우_400반환() throws Exception {
        willThrow(new BusinessException(SocialErrorCode.CANNOT_FOLLOW_SELF))
                .given(relationCommandUseCase).follow(LOGIN_USER_ID, "me");

        mockMvc.perform(post("/users/me/follow"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void follow_이미팔로우중_409반환() throws Exception {
        willThrow(new BusinessException(SocialErrorCode.ALREADY_FOLLOWING))
                .given(relationCommandUseCase).follow(LOGIN_USER_ID, "targetUser");

        mockMvc.perform(post("/users/targetUser/follow"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ── DELETE /users/{username}/follow ────────────────────────

    @Test
    void unfollow_성공_204반환_로그인사용자로언팔로우() throws Exception {
        mockMvc.perform(delete("/users/targetUser/follow"))
                .andExpect(status().isNoContent());

        then(relationCommandUseCase).should().unfollow(LOGIN_USER_ID, "targetUser");
    }

    @Test
    void unfollow_쿼리의userId는무시_로그인사용자로언팔로우() throws Exception {
        // 예전 방식처럼 ?userId=999를 붙여도 로그인 사용자로만 언팔로우해야 됨
        mockMvc.perform(delete("/users/targetUser/follow").param("userId", "999"))
                .andExpect(status().isNoContent());

        then(relationCommandUseCase).should().unfollow(LOGIN_USER_ID, "targetUser");
        then(relationCommandUseCase).shouldHaveNoMoreInteractions();
    }

    @Test
    void unfollow_팔로우관계없음_404반환() throws Exception {
        willThrow(new BusinessException(SocialErrorCode.FOLLOW_NOT_FOUND))
                .given(relationCommandUseCase).unfollow(LOGIN_USER_ID, "targetUser");

        mockMvc.perform(delete("/users/targetUser/follow"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
