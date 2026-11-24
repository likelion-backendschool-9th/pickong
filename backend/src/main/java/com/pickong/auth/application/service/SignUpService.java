package com.pickong.auth.application.service;

import com.pickong.auth.application.dto.SignUpCommand;
import com.pickong.auth.application.dto.SignUpResult;
import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.auth.application.port.in.SignUpUseCase;
import com.pickong.auth.application.port.out.SignupTokenPort;
import com.pickong.auth.application.port.out.SocialAccountPersistencePort;
import com.pickong.auth.domain.AuthErrorCode;
import com.pickong.common.error.BusinessException;
import com.pickong.common.security.LoginUser;
import com.pickong.terms.application.port.in.TermsUseCase;
import com.pickong.user.application.dto.RegisterUserCommand;
import com.pickong.user.application.dto.RegisteredUser;
import com.pickong.user.application.port.in.UserAccountUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 흐름을 맡고, 회원 데이터는 user 모듈, 약관 동의는 terms 모듈의 공개 포트로 처리합니다. (auth → user · terms 의존, blueprint 6.2)
 */
@Service
public class SignUpService implements SignUpUseCase {

    private final SignupTokenPort signupTokens;
    private final SocialAccountPersistencePort socialAccounts;
    private final UserAccountUseCase userAccount;
    private final TermsUseCase terms;

    public SignUpService(SignupTokenPort signupTokens, SocialAccountPersistencePort socialAccounts,
            UserAccountUseCase userAccount, TermsUseCase terms) {
        this.signupTokens = signupTokens;
        this.socialAccounts = socialAccounts;
        this.userAccount = userAccount;
        this.terms = terms;
    }

    @Override
    public SocialProfile getSession(String signupToken, LoginUser currentUser) {
        return verifyToken(signupToken, currentUser);
    }

    /**
     * 순서대로:
     * <ol>
     *   <li>임시 가입 토큰 확인 (없거나 만료 → 401, 이미 로그인한 상태 → 409 ALREADY_REGISTERED)</li>
     *   <li>이 소셜 계정이 이미 가입돼 있는지 (같은 토큰으로 두 번 제출)</li>
     *   <li>인증된 이메일이 그 사이 다른 계정에 연결됐는지 (로그인 때 확인했지만 30분 사이에 바뀔 수 있음)</li>
     *   <li>회원 생성 (user 모듈: 닉네임 · 이름 · 이메일 규칙)</li>
     *   <li>소셜 계정 연결</li>
     *   <li>약관 동의 기록 (terms 모듈: 필수 약관 확인)</li>
     * </ol>
     * 한 트랜잭션이라 6번에서 필수 약관이 빠졌으면 4~5번도 함께 롤백되어 회원이 남지 않습니다.
     */
    @Override
    @Transactional
    public SignUpResult signUp(String signupToken, LoginUser currentUser, SignUpCommand command) {
        SocialProfile profile = verifyToken(signupToken, currentUser);

        if (socialAccounts.findByProvider(profile.provider(), profile.providerUserId()).isPresent()) {
            throw new BusinessException(AuthErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
        }
        if (profile.email() != null && socialAccounts.findAnyByEmail(profile.email()).isPresent()) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        String profileImageUrl = command.useSocialProfileImage() ? profile.profileImageUrl() : null;
        RegisteredUser user = userAccount.register(
                new RegisterUserCommand(command.username(), command.name(), profile.email(), profileImageUrl));
        socialAccounts.link(user.userId(), profile.provider(), profile.providerUserId(), profile.email());
        terms.agreeOnSignup(user.userId(), command.agreedTerms());
        return new SignUpResult(user.userId(), user.username());
    }

    /** 임시 가입 토큰이 유효하면 소셜 계정 정보를, 아니면 상황에 맞는 오류를 던집니다. */
    private SocialProfile verifyToken(String signupToken, LoginUser currentUser) {
        return signupTokens.verify(signupToken).orElseThrow(() -> new BusinessException(
                currentUser != null ? AuthErrorCode.ALREADY_REGISTERED : AuthErrorCode.SIGNUP_SESSION_EXPIRED));
    }
}
