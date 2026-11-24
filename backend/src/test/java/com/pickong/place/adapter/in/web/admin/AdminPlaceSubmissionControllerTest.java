package com.pickong.place.adapter.in.web.admin;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.common.domain.GeoPoint;
import com.pickong.common.error.BusinessException;
import com.pickong.common.error.GlobalExceptionHandler;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.common.security.LoginUser;
import com.pickong.common.web.CurrentAdminArgumentResolver;
import com.pickong.place.application.dto.PlaceSubmissionView;
import com.pickong.place.application.dto.SubmitterView;
import com.pickong.place.application.port.in.PlaceSubmissionUseCase;
import com.pickong.place.domain.PlaceErrorCode;
import com.pickong.place.domain.model.PlaceSubmission;
import com.pickong.place.domain.vo.PlaceSubmissionStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/** 관리자 제보 조회. 보안 필터는 끼우지 않고 응답 모양만 확인합니다. */
@ExtendWith(MockitoExtension.class)
class AdminPlaceSubmissionControllerTest {

    private static final OffsetDateTime SUBMITTED_AT = OffsetDateTime.of(2026, 11, 24, 5, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID PLACE_PUBLIC_ID = UUID.fromString("6f1e8c2a-1b4d-4e7a-9c3d-0a1b2c3d4e5f");

    @Mock
    PlaceSubmissionUseCase placeSubmissions;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminPlaceSubmissionController(placeSubmissions))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentAdminArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()))
                .build();
    }

    @AfterEach
    void clearAdmin() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAdmin() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(7L), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Test
    @DisplayName("목록은 페이지 없이 배열이고 식별자는 문자열이다")
    void listReturnsArray() throws Exception {
        signInAdmin();
        given(placeSubmissions.list()).willReturn(List.of(view(pending(), null)));

        mockMvc.perform(get("/admin/place-submissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].name").value("책방"))
                .andExpect(jsonPath("$[0].lat").value(37.5628))
                .andExpect(jsonPath("$[0].lng").value(126.9254))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].submitter.username").value("seoyeon"))
                .andExpect(jsonPath("$[0].submitter.nickname").value("서연"))
                .andExpect(jsonPath("$[0].placeId").value(nullValue()))
                .andExpect(jsonPath("$[0].page").doesNotExist())
                .andExpect(jsonPath("$[0].totalCount").doesNotExist());
    }

    @Test
    @DisplayName("상세는 승인된 장소의 공개 id를 문자열로 보낸다")
    void detailReturnsPublicPlaceId() throws Exception {
        signInAdmin();
        PlaceSubmission approved = pending().toBuilder()
                .status(PlaceSubmissionStatus.APPROVED)
                .placeId(9L)
                .reviewerAdminId(7L)
                .reviewedAt(SUBMITTED_AT)
                .build();
        given(placeSubmissions.get(1L)).willReturn(view(approved, PLACE_PUBLIC_ID));

        mockMvc.perform(get("/admin/place-submissions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.placeId").value(PLACE_PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.submittedAt").value("2026-11-24T05:00:00Z"));
    }

    @Test
    @DisplayName("없는 제보는 공통 오류 JSON")
    void missingSubmission() throws Exception {
        signInAdmin();
        given(placeSubmissions.get(4L)).willThrow(new BusinessException(PlaceErrorCode.PLACE_SUBMISSION_NOT_FOUND));

        mockMvc.perform(get("/admin/place-submissions/4"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("PLACE_SUBMISSION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("제보를 찾을 수 없어요."));
    }

    @Test
    @DisplayName("제보 id가 숫자가 아니면 400")
    void invalidId() throws Exception {
        signInAdmin();
        mockMvc.perform(get("/admin/place-submissions/ps-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."));
    }

    @Test
    @DisplayName("승인은 관리자 주체의 id로 장소를 연결하고 본문 id는 무시한다")
    void approveUsesAdminPrincipal() throws Exception {
        signInAdmin();
        PlaceSubmission approved = pending().toBuilder()
                .status(PlaceSubmissionStatus.APPROVED)
                .placeId(9L)
                .reviewerAdminId(7L)
                .reviewedAt(SUBMITTED_AT)
                .build();
        given(placeSubmissions.approve(1L, 7L)).willReturn(view(approved, PLACE_PUBLIC_ID));

        mockMvc.perform(post("/admin/place-submissions/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerAdminId\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.placeId").value(PLACE_PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.reviewerAdminId").doesNotExist());
        verify(placeSubmissions).approve(1L, 7L);
    }

    @Test
    @DisplayName("반려는 장소를 만들지 않고 사유만 반영한다")
    void rejectDoesNotCreatePlace() throws Exception {
        signInAdmin();
        PlaceSubmission rejected = pending().toBuilder()
                .status(PlaceSubmissionStatus.REJECTED)
                .reviewerAdminId(7L)
                .reviewedAt(SUBMITTED_AT)
                .rejectReason("위치가 불명확합니다")
                .build();
        given(placeSubmissions.reject(1L, 7L, "위치가 불명확합니다")).willReturn(view(rejected, null));

        mockMvc.perform(post("/admin/place-submissions/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"위치가 불명확합니다\",\"reviewerAdminId\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("위치가 불명확합니다"))
                .andExpect(jsonPath("$.placeId").value(nullValue()));
        verify(placeSubmissions).reject(1L, 7L, "위치가 불명확합니다");
        verify(placeSubmissions, never()).approve(anyLong(), anyLong());
    }

    @Test
    @DisplayName("이미 처리한 제보를 다시 승인하면 409")
    void approveAlreadyReviewed() throws Exception {
        signInAdmin();
        given(placeSubmissions.approve(eq(1L), eq(7L))).willThrow(new BusinessException(PlaceErrorCode.ALREADY_REVIEWED));

        mockMvc.perform(post("/admin/place-submissions/1/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("ALREADY_REVIEWED"))
                .andExpect(jsonPath("$.message").value("이미 처리한 제보예요."));
    }

    @Test
    @DisplayName("빈 반려 사유는 400")
    void blankRejectReason() throws Exception {
        signInAdmin();
        given(placeSubmissions.reject(1L, 7L, "  ")).willThrow(new BusinessException(PlaceErrorCode.REJECT_REASON_REQUIRED));

        mockMvc.perform(post("/admin/place-submissions/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REJECT_REASON_REQUIRED"))
                .andExpect(jsonPath("$.message").value("반려 사유를 입력해 주세요."));
    }

    @Test
    @DisplayName("회원 토큰은 심사자가 되지 않는다")
    void memberCannotReview() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(3L), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mockMvc.perform(post("/admin/place-submissions/1/approve"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(placeSubmissions, never()).approve(anyLong(), anyLong());
    }

    @Test
    @DisplayName("관리자 주체가 없으면 승인하지 않는다")
    void anonymousCannotReview() throws Exception {
        mockMvc.perform(post("/admin/place-submissions/1/approve"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(placeSubmissions, never()).approve(anyLong(), anyLong());
    }

    private static PlaceSubmission pending() {
        return PlaceSubmission.submit(3L, "책방", "서울", "SHOP", "설명", null, new GeoPoint(37.5628, 126.9254), SUBMITTED_AT)
                .toBuilder()
                .id(1L)
                .build();
    }

    private static PlaceSubmissionView view(PlaceSubmission submission, UUID placePublicId) {
        return new PlaceSubmissionView(submission, new SubmitterView("user-public", "seoyeon", "서연"), placePublicId);
    }
}
