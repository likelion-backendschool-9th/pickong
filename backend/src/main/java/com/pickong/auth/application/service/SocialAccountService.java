package com.pickong.auth.application.service;

import com.pickong.auth.application.dto.ConnectedSocialAccount;
import com.pickong.auth.application.dto.LoginMethod;
import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.auth.application.port.in.SocialAccountUseCase;
import com.pickong.auth.application.port.out.LinkTokenPort;
import com.pickong.auth.application.port.out.SocialAccountPersistencePort;
import com.pickong.auth.domain.AuthErrorCode;
import com.pickong.auth.domain.vo.SocialProvider;
import com.pickong.common.error.BusinessException;
import com.pickong.user.application.port.in.UserAccountUseCase;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 수단(소셜 계정) 연결 · 목록 · 해제.
 * <p>
 * 규칙:
 * <ul>
 *   <li>제공자마다 하나씩, 같은 소셜 계정은 한 회원에만 연결 (DB UNIQUE가 최종 보장)</li>
 *   <li>연결하려는 계정의 이메일이 다른 회원 것이면 막음 (1인 1계정, 자동 연결 없음)</li>
 *   <li><b>가입할 때 쓴 수단은 해제할 수 없음.</b> 가입 수단 = 가장 먼저 연결된 소셜 계정.
 *       가입할 때 첫 연결이 만들어지고 그 줄은 지울 수 없으니 계속 첫 번째로 남습니다(DB 컬럼 없이 판별).
 *       그래서 '마지막 남은 수단을 해제'하는 일도 저절로 생기지 않습니다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class SocialAccountService implements SocialAccountUseCase {

    private final SocialAccountPersistencePort socialAccounts;
    private final LinkTokenPort linkTokens;
    private final UserAccountUseCase userAccount;

    public SocialAccountService(SocialAccountPersistencePort socialAccounts, LinkTokenPort linkTokens,
            UserAccountUseCase userAccount) {
        this.socialAccounts = socialAccounts;
        this.linkTokens = linkTokens;
        this.userAccount = userAccount;
    }

    /** 연결 순서대로 돌려주고, 첫 번째(가입 수단)만 해제할 수 없다고 표시합니다. */
    @Override
    public List<LoginMethod> list(Long userId) {
        List<ConnectedSocialAccount> mine = socialAccounts.findAllByUserId(userId);
        return IntStream.range(0, mine.size())
                .mapToObj(i -> {
                    ConnectedSocialAccount account = mine.get(i);
                    return new LoginMethod(account.provider(), account.email(), account.connectedAt(), i > 0);
                })
                .toList();
    }

    /**
     * 이미 연결된 제공자면 제공자 화면으로 보내기 전에 바로 막습니다(헛걸음 방지).
     * 통과하면 연결 토큰만 만들고, 실제 연결은 제공자 인증을 마친 뒤 {@link #link}에서 합니다.
     */
    @Override
    public String startLink(Long userId, SocialProvider provider) {
        if (findMine(userId, provider).isPresent()) {
            throw new BusinessException(AuthErrorCode.PROVIDER_ALREADY_LINKED);
        }
        return linkTokens.issue(userId, provider);
    }

    @Override
    public Optional<Long> verifyLinkToken(String linkToken, SocialProvider provider) {
        return linkTokens.verify(linkToken, provider);
    }

    /**
     * 순서대로 확인한 뒤 연결합니다.
     * <ol>
     *   <li>회원이 로그인할 수 있는 상태인지 (연결을 시작한 뒤 정지됐을 수 있음)</li>
     *   <li>이 소셜 계정이 이미 연결돼 있나 → 내 계정이면 그대로 성공, 남의 계정이면 SOCIAL_ACCOUNT_ALREADY_LINKED</li>
     *   <li>내 계정에 같은 제공자의 <b>다른</b> 계정이 있나 → PROVIDER_ALREADY_LINKED</li>
     *   <li>인증된 이메일이 다른 회원의 소셜 계정에 있나 → EMAIL_ALREADY_REGISTERED
     *       (다른 회원의 가입 이메일 users.email도 그 회원의 첫 소셜 계정 이메일이라 함께 걸립니다.
     *       내 카카오 · Google 이메일이 같은 것은 괜찮습니다)</li>
     * </ol>
     * 2 · 3을 동시에 통과한 요청끼리는 DB UNIQUE가 막고 저장소가 같은 오류 코드로 바꿉니다.
     */
    @Override
    @Transactional
    public void link(Long userId, SocialProfile profile) {
        if (!userAccount.canLogin(userId)) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_UNAVAILABLE);
        }
        var linked = socialAccounts.findByProvider(profile.provider(), profile.providerUserId());
        if (linked.isPresent()) {
            if (linked.get().userId().equals(userId)) {
                return;
            }
            throw new BusinessException(AuthErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
        }
        if (findMine(userId, profile.provider()).isPresent()) {
            throw new BusinessException(AuthErrorCode.PROVIDER_ALREADY_LINKED);
        }
        if (profile.email() != null && socialAccounts.existsEmailOnOtherUser(profile.email(), userId)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        socialAccounts.link(userId, profile.provider(), profile.providerUserId(), profile.email());
    }

    /** 첫 번째(가입 수단)가 아니면 지웁니다. 해제해도 로그인 세션은 그대로입니다. */
    @Override
    @Transactional
    public void unlink(Long userId, SocialProvider provider) {
        List<ConnectedSocialAccount> mine = socialAccounts.findAllByUserId(userId);
        ConnectedSocialAccount target = mine.stream()
                .filter(account -> account.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new BusinessException(AuthErrorCode.SOCIAL_ACCOUNT_NOT_LINKED));
        if (target.id().equals(mine.getFirst().id())) {
            throw new BusinessException(AuthErrorCode.SIGNUP_METHOD_CANNOT_BE_UNLINKED);
        }
        socialAccounts.delete(target.id());
    }

    private Optional<ConnectedSocialAccount> findMine(Long userId, SocialProvider provider) {
        return socialAccounts.findAllByUserId(userId).stream()
                .filter(account -> account.provider() == provider)
                .findFirst();
    }
}
