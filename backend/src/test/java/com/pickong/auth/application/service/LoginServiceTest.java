package com.pickong.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pickong.auth.application.dto.LinkedSocialAccount;
import com.pickong.auth.application.dto.SocialLoginResult;
import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.auth.application.port.out.RestoreTokenPort;
import com.pickong.auth.application.port.out.SignupTokenPort;
import com.pickong.auth.application.port.out.SocialAccountPersistencePort;
import com.pickong.auth.domain.vo.SocialProvider;
import com.pickong.user.application.dto.WithdrawalStatus;
import com.pickong.user.application.port.in.UserAccountUseCase;
import com.pickong.user.application.port.in.UserWithdrawalUseCase;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 소셜 로그인 판단 단위 테스트 (DB 없이 Mockito). */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    SocialAccountPersistencePort socialAccounts;

    @Mock
    UserAccountUseCase userAccount;

    @Mock
    SignupTokenPort signupTokens;

    @Mock
    UserWithdrawalUseCase userWithdrawal;

    @Mock
    RestoreTokenPort restoreTokens;

    @InjectMocks
    LoginService loginService;

    private static final SocialProfile KAKAO = new SocialProfile(SocialProvider.KAKAO, "4242", "kong@kakao.com", "콩이", null);

    @Test
    @DisplayName("연결된 회원이 있고 ACTIVE면 기존 회원")
    void existingMember() {
        given(socialAccounts.findByProvider(SocialProvider.KAKAO, "4242"))
                .willReturn(Optional.of(new LinkedSocialAccount(7L, SocialProvider.KAKAO, "4242", "kong@kakao.com")));
        given(userAccount.canLogin(7L)).willReturn(true);

        assertThat(loginService.loginWithSocial(KAKAO)).isEqualTo(new SocialLoginResult.ExistingMember(7L));
        verify(signupTokens, never()).issue(KAKAO);
    }

    @Test
    @DisplayName("연결된 회원이 정지 · 탈퇴(취소할 수 없음)면 이용 불가")
    void unavailableMember() {
        given(socialAccounts.findByProvider(SocialProvider.KAKAO, "4242"))
                .willReturn(Optional.of(new LinkedSocialAccount(7L, SocialProvider.KAKAO, "4242", null)));
        given(userAccount.canLogin(7L)).willReturn(false);
        given(userWithdrawal.findCancellableWithdrawal(7L)).willReturn(Optional.empty());

        assertThat(loginService.loginWithSocial(KAKAO)).isInstanceOf(SocialLoginResult.Unavailable.class);
        verify(restoreTokens, never()).issue(7L);
    }

    @Test
    @DisplayName("연결된 회원이 탈퇴 유예 중(30일 안)이면 로그인 세션 대신 탈퇴 취소 토큰")
    void withdrawalPendingMember() {
        given(socialAccounts.findByProvider(SocialProvider.KAKAO, "4242"))
                .willReturn(Optional.of(new LinkedSocialAccount(7L, SocialProvider.KAKAO, "4242", null)));
        given(userAccount.canLogin(7L)).willReturn(false);
        given(userWithdrawal.findCancellableWithdrawal(7L)).willReturn(Optional.of(new WithdrawalStatus("seoyeon",
                OffsetDateTime.parse("2026-11-20T00:00:00Z"), OffsetDateTime.parse("2026-12-20T00:00:00Z"))));
        given(restoreTokens.issue(7L)).willReturn("restore-token");

        assertThat(loginService.loginWithSocial(KAKAO)).isEqualTo(new SocialLoginResult.WithdrawalPending("restore-token"));
    }

    @Test
    @DisplayName("처음 온 계정인데 이메일이 다른 회원(Google로 가입)과 겹치면 가입을 막고 그 제공자를 알려 준다")
    void emailAlreadyRegistered() {
        given(socialAccounts.findByProvider(SocialProvider.KAKAO, "4242")).willReturn(Optional.empty());
        given(socialAccounts.findAnyByEmail("kong@kakao.com"))
                .willReturn(Optional.of(new LinkedSocialAccount(9L, SocialProvider.GOOGLE, "1089", "kong@kakao.com")));

        assertThat(loginService.loginWithSocial(KAKAO))
                .isEqualTo(new SocialLoginResult.EmailAlreadyRegistered(SocialProvider.GOOGLE));
        verify(signupTokens, never()).issue(KAKAO);
    }

    @Test
    @DisplayName("처음 온 계정이고 이메일도 겹치지 않으면 임시 가입 토큰을 발급한다")
    void newMember() {
        given(socialAccounts.findByProvider(SocialProvider.KAKAO, "4242")).willReturn(Optional.empty());
        given(socialAccounts.findAnyByEmail("kong@kakao.com")).willReturn(Optional.empty());
        given(signupTokens.issue(KAKAO)).willReturn("signup-token");

        assertThat(loginService.loginWithSocial(KAKAO)).isEqualTo(new SocialLoginResult.NewMember("signup-token"));
    }

    @Test
    @DisplayName("이메일이 없는 계정(카카오 이메일 미동의)은 이메일 확인 없이 신규 회원")
    void newMemberWithoutEmail() {
        SocialProfile noEmail = new SocialProfile(SocialProvider.KAKAO, "4242", null, "콩이", null);
        given(socialAccounts.findByProvider(SocialProvider.KAKAO, "4242")).willReturn(Optional.empty());
        given(signupTokens.issue(noEmail)).willReturn("signup-token");

        assertThat(loginService.loginWithSocial(noEmail)).isEqualTo(new SocialLoginResult.NewMember("signup-token"));
        verify(socialAccounts, never()).findAnyByEmail(org.mockito.ArgumentMatchers.anyString());
    }
}
