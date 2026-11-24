package com.pickong.auth.application.service;

import com.pickong.auth.application.dto.SocialLoginResult;
import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.auth.application.port.in.LoginUseCase;
import com.pickong.auth.application.port.out.SignupTokenPort;
import com.pickong.auth.application.port.out.SocialAccountPersistencePort;
import com.pickong.user.application.port.in.UserAccountUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 판단. 토큰 발급은 하지 않고 '무엇을 할지'만 정합니다.
 * (세션 발급은 TokenUseCase가 하고, 둘을 이어 붙이는 일은 웹 어댑터가 합니다. 서비스끼리 서로 부르지 않기 위해서)
 */
@Service
@Transactional(readOnly = true)
public class LoginService implements LoginUseCase {

    private final SocialAccountPersistencePort socialAccounts;
    private final UserAccountUseCase userAccount;
    private final SignupTokenPort signupTokens;

    public LoginService(SocialAccountPersistencePort socialAccounts, UserAccountUseCase userAccount,
            SignupTokenPort signupTokens) {
        this.socialAccounts = socialAccounts;
        this.userAccount = userAccount;
        this.signupTokens = signupTokens;
    }

    /**
     * 순서대로 판단합니다.
     * <ol>
     *   <li>이 소셜 계정이 연결된 회원이 있다 → 로그인 가능한 상태면 기존 회원, 아니면 이용 불가</li>
     *   <li>없다(신규) → 인증된 이메일이 이미 다른 계정에 쓰이고 있으면 가입을 막는다 (1인 1계정)</li>
     *   <li>그 외 → 임시 가입 토큰을 발급해 온보딩으로</li>
     * </ol>
     * 이메일 확인은 연결된 소셜 계정의 이메일로 합니다. 가입 이메일(users.email)도 첫 소셜 계정의 이메일과 같아서 함께 걸립니다.
     */
    @Override
    public SocialLoginResult loginWithSocial(SocialProfile profile) {
        var linked = socialAccounts.findByProvider(profile.provider(), profile.providerUserId());
        if (linked.isPresent()) {
            Long userId = linked.get().userId();
            return userAccount.canLogin(userId)
                    ? new SocialLoginResult.ExistingMember(userId)
                    : new SocialLoginResult.Unavailable();
        }

        if (profile.email() != null) {
            var sameEmail = socialAccounts.findAnyByEmail(profile.email());
            if (sameEmail.isPresent()) {
                return new SocialLoginResult.EmailAlreadyRegistered(sameEmail.get().provider());
            }
        }
        return new SocialLoginResult.NewMember(signupTokens.issue(profile));
    }
}
