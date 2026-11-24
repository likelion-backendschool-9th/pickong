package com.pickong.place.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.common.domain.GeoPoint;
import com.pickong.common.error.GlobalExceptionHandler;
import com.pickong.common.security.LoginUser;
import com.pickong.common.web.CurrentUserArgumentResolver;
import com.pickong.place.application.dto.PlaceSubmissionView;
import com.pickong.place.application.dto.SubmitPlaceCommand;
import com.pickong.place.application.dto.SubmitterView;
import com.pickong.place.application.port.in.PlaceSubmissionUseCase;
import com.pickong.place.domain.model.PlaceSubmission;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
class PlaceSubmissionControllerTest {

    @Mock
    PlaceSubmissionUseCase placeSubmissions;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlaceSubmissionController(placeSubmissions))
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
    @DisplayName("제보는 로그인한 회원의 대기 건이고 공개 장소 id는 없다")
    void submitStaysPending() throws Exception {
        signIn(3L);
        PlaceSubmission pending = PlaceSubmission.submit(3L, "책방", null, "SHOP", "설명", null,
                new GeoPoint(37.5628, 126.9254), OffsetDateTime.of(2026, 11, 24, 5, 0, 0, 0, ZoneOffset.UTC))
                .toBuilder().id(8L).build();
        given(placeSubmissions.submit(any())).willReturn(new PlaceSubmissionView(pending,
                new SubmitterView("user-public", "seoyeon", "서연"), null));

        mockMvc.perform(post("/place-submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"책방","lat":37.5628,"lng":126.9254,"description":"설명","category":"SHOP","imageUrl":null,"userId":99}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("8"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.placeId").value(nullValue()));

        ArgumentCaptor<SubmitPlaceCommand> command = ArgumentCaptor.forClass(SubmitPlaceCommand.class);
        verify(placeSubmissions).submit(command.capture());
        assertThat(command.getValue().submitterUserId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("좌표가 없으면 제보를 저장하지 않는다")
    void missingCoordinates() throws Exception {
        signIn(3L);

        mockMvc.perform(post("/place-submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"책방\",\"category\":\"SHOP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"));
        verify(placeSubmissions, never()).submit(any());
    }

    @Test
    @DisplayName("로그인하지 않으면 제보하지 않는다")
    void anonymous() throws Exception {
        mockMvc.perform(post("/place-submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"책방\",\"lat\":37.5,\"lng\":126.9,\"category\":\"SHOP\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(placeSubmissions, never()).submit(any());
    }

    private static void signIn(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new LoginUser(userId), null, List.of()));
    }
}
