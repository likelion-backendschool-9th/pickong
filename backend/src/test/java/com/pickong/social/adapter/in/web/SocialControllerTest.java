package com.pickong.social.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickong.common.error.BusinessException;
import com.pickong.common.error.GlobalExceptionHandler;
import com.pickong.social.application.port.in.RelationCommandUseCase;
import com.pickong.social.domain.SocialErrorCode;
import com.pickong.user.domain.UserErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SocialControllerTest {

    @Mock
    private RelationCommandUseCase relationCommandUseCase;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SocialController(relationCommandUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── POST /users/{username}/follow ──────────────────────────

    @Test
    void follow_성공_201반환() throws Exception {
        Map<String, Object> body = Map.of("userId", 1);

        mockMvc.perform(post("/users/targetUser/follow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void follow_userId누락_400반환() throws Exception {
        mockMvc.perform(post("/users/targetUser/follow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void follow_존재하지않는사용자_404반환() throws Exception {
        willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND))
                .given(relationCommandUseCase).follow(anyLong(), anyString());
        Map<String, Object> body = Map.of("userId", 1);

        mockMvc.perform(post("/users/nobody/follow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void follow_자기자신팔로우_400반환() throws Exception {
        willThrow(new BusinessException(SocialErrorCode.CANNOT_FOLLOW_SELF))
                .given(relationCommandUseCase).follow(anyLong(), anyString());
        Map<String, Object> body = Map.of("userId", 1);

        mockMvc.perform(post("/users/me/follow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void follow_이미팔로우중_409반환() throws Exception {
        willThrow(new BusinessException(SocialErrorCode.ALREADY_FOLLOWING))
                .given(relationCommandUseCase).follow(anyLong(), anyString());
        Map<String, Object> body = Map.of("userId", 1);

        mockMvc.perform(post("/users/targetUser/follow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ── DELETE /users/{username}/follow ────────────────────────

    @Test
    void unfollow_성공_204반환() throws Exception {
        mockMvc.perform(delete("/users/targetUser/follow")
                        .param("userId", "1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void unfollow_userId누락_400반환() throws Exception {
        mockMvc.perform(delete("/users/targetUser/follow"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unfollow_팔로우관계없음_404반환() throws Exception {
        willThrow(new BusinessException(SocialErrorCode.FOLLOW_NOT_FOUND))
                .given(relationCommandUseCase).unfollow(anyLong(), anyString());

        mockMvc.perform(delete("/users/targetUser/follow")
                        .param("userId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
