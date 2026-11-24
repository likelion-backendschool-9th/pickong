package com.pickong.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pickong.auth.application.dto.ConnectedSocialAccount;
import com.pickong.auth.application.dto.LinkedSocialAccount;
import com.pickong.auth.application.dto.LoginMethod;
import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.auth.application.port.out.LinkTokenPort;
import com.pickong.auth.application.port.out.SocialAccountPersistencePort;
import com.pickong.auth.domain.AuthErrorCode;
import com.pickong.auth.domain.vo.SocialProvider;
import com.pickong.common.error.BusinessException;
import com.pickong.common.error.ErrorCode;
import com.pickong.user.application.port.in.UserAccountUseCase;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 로그인 수단 연결 · 해제 규칙 단위 테스트 (DB 없이 Mockito). */
@ExtendWith(MockitoExtension.class)
class SocialAccountServiceTest {

    @Mock
    SocialAccountPersistencePort socialAccounts;

    @Mock
    LinkTokenPort linkTokens;

    @Mock
    UserAccountUseCase userAccount;

    @InjectMocks
    SocialAccountService service;

    private static final long ME = 7L;
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-11-01T00:00:00+09:00");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-11-20T00:00:00+09:00");

    /** 카카오로 가입한 회원 (첫 연결 = 가입 수단) */
    private static final ConnectedSocialAccount MY_KAKAO = new ConnectedSocialAccount(1L, SocialProvider.KAKAO, "kong@kakao.com", T1);
    private static final ConnectedSocialAccount MY_GOOGLE = new ConnectedSocialAccount(2L, SocialProvider.GOOGLE, "kong@gmail.com", T2);

    private static final SocialProfile GOOGLE = new SocialProfile(SocialProvider.GOOGLE, "1089", "kong@gmail.com", "콩", null);

    private void iHave(ConnectedSocialAccount... accounts) {
        given(socialAccounts.findAllByUserId(ME)).willReturn(List.of(accounts));
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(code);
    }

    // ── 목록 ───────────────────────────────────────────────

    @Test
    @DisplayName("목록: 연결 순서대로, 가입 수단(첫 번째)만 해제할 수 없다")
    void listMarksSignupMethod() {
        iHave(MY_KAKAO, MY_GOOGLE);

        assertThat(service.list(ME)).containsExactly(
                new LoginMethod(SocialProvider.KAKAO, "kong@kakao.com", T1, false),
                new LoginMethod(SocialProvider.GOOGLE, "kong@gmail.com", T2, true));
    }

    // ── 연결 시작 ───────────────────────────────────────────

    @Test
    @DisplayName("연결 시작: 아직 없는 제공자면 연결 토큰을 발급한다")
    void startLinkIssuesToken() {
        iHave(MY_KAKAO);
        given(linkTokens.issue(ME, SocialProvider.GOOGLE)).willReturn("link-token");

        assertThat(service.startLink(ME, SocialProvider.GOOGLE)).isEqualTo("link-token");
    }

    @Test
    @DisplayName("연결 시작: 이미 연결된 제공자면 제공자 화면으로 보내기 전에 PROVIDER_ALREADY_LINKED")
    void startLinkAlreadyLinked() {
        iHave(MY_KAKAO);

        assertError(() -> service.startLink(ME, SocialProvider.KAKAO), AuthErrorCode.PROVIDER_ALREADY_LINKED);
        verify(linkTokens, never()).issue(anyLong(), any());
    }

    // ── 연결 (콜백) ─────────────────────────────────────────

    @Test
    @DisplayName("연결: 규칙을 모두 통과하면 소셜 계정을 저장한다")
    void linkSaves() {
        given(userAccount.canLogin(ME)).willReturn(true);
        given(socialAccounts.findByProvider(SocialProvider.GOOGLE, "1089")).willReturn(Optional.empty());
        iHave(MY_KAKAO);
        given(socialAccounts.existsEmailOnOtherUser("kong@gmail.com", ME)).willReturn(false);

        service.link(ME, GOOGLE);

        verify(socialAccounts).link(ME, SocialProvider.GOOGLE, "1089", "kong@gmail.com");
    }

    @Test
    @DisplayName("연결: 이미 내 계정에 연결된 같은 소셜 계정이면 아무것도 하지 않고 성공 (새로고침 · 두 번 클릭)")
    void linkSameAccountIsNoop() {
        given(userAccount.canLogin(ME)).willReturn(true);
        given(socialAccounts.findByProvider(SocialProvider.GOOGLE, "1089"))
                .willReturn(Optional.of(new LinkedSocialAccount(ME, SocialProvider.GOOGLE, "1089", "kong@gmail.com")));

        service.link(ME, GOOGLE);

        verify(socialAccounts, never()).link(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("연결: 이 소셜 계정이 다른 회원에 연결돼 있으면 SOCIAL_ACCOUNT_ALREADY_LINKED")
    void linkOwnedByOther() {
        given(userAccount.canLogin(ME)).willReturn(true);
        given(socialAccounts.findByProvider(SocialProvider.GOOGLE, "1089"))
                .willReturn(Optional.of(new LinkedSocialAccount(9L, SocialProvider.GOOGLE, "1089", "kong@gmail.com")));

        assertError(() -> service.link(ME, GOOGLE), AuthErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
    }

    @Test
    @DisplayName("연결: 내 계정에 다른 Google 계정이 이미 있으면 PROVIDER_ALREADY_LINKED (제공자마다 하나)")
    void linkProviderTaken() {
        given(userAccount.canLogin(ME)).willReturn(true);
        given(socialAccounts.findByProvider(SocialProvider.GOOGLE, "1089")).willReturn(Optional.empty());
        iHave(MY_KAKAO, MY_GOOGLE);

        assertError(() -> service.link(ME, GOOGLE), AuthErrorCode.PROVIDER_ALREADY_LINKED);
    }

    @Test
    @DisplayName("연결: 인증된 이메일이 다른 회원 것이면 EMAIL_ALREADY_REGISTERED (1인 1계정)")
    void linkEmailOfOther() {
        given(userAccount.canLogin(ME)).willReturn(true);
        given(socialAccounts.findByProvider(SocialProvider.GOOGLE, "1089")).willReturn(Optional.empty());
        iHave(MY_KAKAO);
        given(socialAccounts.existsEmailOnOtherUser("kong@gmail.com", ME)).willReturn(true);

        assertError(() -> service.link(ME, GOOGLE), AuthErrorCode.EMAIL_ALREADY_REGISTERED);
        verify(socialAccounts, never()).link(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("연결: 이메일이 없으면(카카오 미동의 등) 이메일 확인 없이 연결한다")
    void linkWithoutEmail() {
        SocialProfile noEmail = new SocialProfile(SocialProvider.GOOGLE, "1089", null, null, null);
        given(userAccount.canLogin(ME)).willReturn(true);
        given(socialAccounts.findByProvider(SocialProvider.GOOGLE, "1089")).willReturn(Optional.empty());
        iHave(MY_KAKAO);

        service.link(ME, noEmail);

        verify(socialAccounts, never()).existsEmailOnOtherUser(any(), anyLong());
        verify(socialAccounts).link(ME, SocialProvider.GOOGLE, "1089", null);
    }

    @Test
    @DisplayName("연결: 연결 중에 정지된 회원이면 ACCOUNT_UNAVAILABLE")
    void linkSuspended() {
        given(userAccount.canLogin(ME)).willReturn(false);

        assertError(() -> service.link(ME, GOOGLE), AuthErrorCode.ACCOUNT_UNAVAILABLE);
    }

    // ── 해제 ───────────────────────────────────────────────

    @Test
    @DisplayName("해제: 나중에 연결한 수단은 해제된다")
    void unlinkLater() {
        iHave(MY_KAKAO, MY_GOOGLE);

        service.unlink(ME, SocialProvider.GOOGLE);

        verify(socialAccounts).delete(2L);
    }

    @Test
    @DisplayName("해제: 가입할 때 쓴 수단은 다른 수단이 있어도 SIGNUP_METHOD_CANNOT_BE_UNLINKED")
    void unlinkSignupMethod() {
        iHave(MY_KAKAO, MY_GOOGLE);

        assertError(() -> service.unlink(ME, SocialProvider.KAKAO), AuthErrorCode.SIGNUP_METHOD_CANNOT_BE_UNLINKED);
        verify(socialAccounts, never()).delete(anyLong());
    }

    @Test
    @DisplayName("해제: 연결돼 있지 않은 제공자면 SOCIAL_ACCOUNT_NOT_LINKED")
    void unlinkNotLinked() {
        iHave(MY_KAKAO);

        assertError(() -> service.unlink(ME, SocialProvider.GOOGLE), AuthErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
    }
}
