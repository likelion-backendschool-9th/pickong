package com.pickong.moderation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.common.error.GlobalExceptionHandler;
import com.pickong.common.security.LoginUser;
import com.pickong.common.web.CurrentUserArgumentResolver;
import com.pickong.moderation.application.dto.ReportCommand;
import com.pickong.moderation.application.dto.ReportReceipt;
import com.pickong.moderation.application.port.in.ReportUseCase;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-11-25T03:00:00Z");

    @Mock
    ReportUseCase reports;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReportController(reports))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()))
                .build();
    }

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("화면의 POST는 저장 값 FEED로 넘기고, 클라이언트가 넣은 본문은 받지 않는다")
    void postMapsToFeedAndDropsClientEvidence() throws Exception {
        signIn(3L);
        org.mockito.BDDMockito.given(reports.report(any())).willReturn(
                new ReportReceipt(5L, "FEED", 41L, "SPAM", "PENDING", CREATED_AT));

        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"POST","targetId":41,"reason":"SPAM","detail":" 광고 ",
                                 "reporterId":99,"text":"조작된 본문","imageUrl":"https://example.test/a.jpg","reportedUserId":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.targetType").value("POST"))
                .andExpect(jsonPath("$.targetId").value(41))
                .andExpect(jsonPath("$.reason").value("SPAM"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.snapshot").doesNotExist())
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.text").doesNotExist());

        ArgumentCaptor<ReportCommand> command = ArgumentCaptor.forClass(ReportCommand.class);
        verify(reports).report(command.capture());
        assertThat(command.getValue().reporterId()).isEqualTo(3L);
        assertThat(command.getValue().targetType()).isEqualTo(ReportTargetType.FEED);
        assertThat(command.getValue().targetId()).isEqualTo(41L);
        assertThat(command.getValue().reason()).isEqualTo("SPAM");
        assertThat(command.getValue().detail()).isEqualTo(" 광고 ");
        assertThat(command.getValue().toString()).doesNotContain("조작된 본문");
    }

    @Test
    @DisplayName("FEED도 게시물로 받고 댓글은 그대로 전달한다")
    void feedAndCommentRequests() throws Exception {
        signIn(3L);
        org.mockito.BDDMockito.given(reports.report(any())).willReturn(
                new ReportReceipt(6L, "FEED", 41L, "SPAM", "PENDING", CREATED_AT),
                new ReportReceipt(7L, "COMMENT", 8L, "ABUSE", "PENDING", CREATED_AT));

        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"FEED\",\"targetId\":41,\"reason\":\"SPAM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetType").value("POST"));
        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\" COMMENT \",\"targetId\":8,\"reason\":\"ABUSE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetType").value("COMMENT"));

        ArgumentCaptor<ReportCommand> command = ArgumentCaptor.forClass(ReportCommand.class);
        verify(reports, org.mockito.Mockito.times(2)).report(command.capture());
        assertThat(command.getAllValues().get(0).targetType()).isEqualTo(ReportTargetType.FEED);
        assertThat(command.getAllValues().get(1).targetType()).isEqualTo(ReportTargetType.COMMENT);
    }

    @Test
    @DisplayName("회원 · 컬렉션 · 대상 없음은 서비스를 호출하지 않는다")
    void unsupportedTargetDoesNotCallTheService() throws Exception {
        signIn(3L);

        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"USER\",\"targetId\":4,\"reason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_TARGET"));
        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"COLLECTION\",\"targetId\":4,\"reason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_TARGET"));
        mockMvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_TARGET"));

        verify(reports, never()).report(any());
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":41,\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(reports, never()).report(any());
    }

    private static void signIn(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new LoginUser(userId), null, java.util.List.of()));
    }
}
