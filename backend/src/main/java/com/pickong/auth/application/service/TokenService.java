package com.pickong.auth.application.service;

import com.pickong.auth.application.dto.IssuedTokens;
import com.pickong.auth.application.port.in.TokenUseCase;
import com.pickong.auth.application.port.out.RefreshTokenPersistencePort;
import com.pickong.auth.application.port.out.TokenIssuerPort;
import com.pickong.auth.domain.AuthErrorCode;
import com.pickong.auth.domain.model.RefreshToken;
import com.pickong.auth.domain.vo.RawRefreshToken;
import com.pickong.common.error.BusinessException;
import com.pickong.user.application.port.in.UserAccountUseCase;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 세션(Access · Refresh Token) 발급 · 재발급 · 로그아웃.
 */
@Service
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class TokenService implements TokenUseCase {

    private final RefreshTokenPersistencePort refreshTokens;
    private final TokenIssuerPort tokenIssuer;
    private final UserAccountUseCase userAccount;
    private final RefreshTokenProperties properties;
    private final Clock clock;

    public TokenService(RefreshTokenPersistencePort refreshTokens, TokenIssuerPort tokenIssuer,
            UserAccountUseCase userAccount, RefreshTokenProperties properties, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.tokenIssuer = tokenIssuer;
        this.userAccount = userAccount;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 로그인할 때 새 세션을 만듭니다. family를 새로 만들어서, 이 기기의 세션을 다른 기기와 따로 관리합니다.
     */
    @Override
    @Transactional
    public IssuedTokens issue(Long userId) {
        return issueInFamily(userId, UUID.randomUUID().toString(), now());
    }

    /**
     * Refresh Token으로 재발급합니다. 순서대로:
     * <ol>
     *   <li>토큰을 DB에서 찾는다 (없으면 401)</li>
     *   <li>상태 확인: 만료 → 401 / 이미 폐기된 토큰(재사용) → 세션 전체 폐기 후 401</li>
     *   <li>이 토큰을 폐기한다. 동시에 같은 토큰으로 온 다른 요청이 먼저 폐기했다면 그것도 재사용으로 본다</li>
     *   <li>회원이 정지 · 탈퇴 상태면 세션 전체 폐기 후 401</li>
     *   <li>같은 family로 새 토큰 쌍을 발급한다 (만료는 다시 14일로 연장)</li>
     * </ol>
     * <b>noRollbackFor:</b> 재사용 · 정지로 세션을 폐기한 뒤 401 예외를 던지는데, 기본 설정이면 예외 때문에
     * 폐기까지 롤백됩니다. 폐기는 반드시 남아야 하므로 BusinessException에서는 롤백하지 않습니다.
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public IssuedTokens refresh(String rawRefreshToken) {
        OffsetDateTime now = now();
        RefreshToken token = refreshTokens.findByTokenHash(new RawRefreshToken(rawRefreshToken).hash())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        switch (token.stateAt(now)) {
            case EXPIRED -> throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            case REUSED -> throw revokeSession(token, now, AuthErrorCode.REFRESH_TOKEN_REUSED);
            case ACTIVE -> {
                // 다른 요청이 같은 토큰을 한발 먼저 썼다면 false → 재사용과 같게 처리
                if (!refreshTokens.revokeIfActive(token.getId(), now)) {
                    throw revokeSession(token, now, AuthErrorCode.REFRESH_TOKEN_REUSED);
                }
            }
        }

        if (!userAccount.canLogin(token.getUserId())) {
            throw revokeSession(token, now, AuthErrorCode.ACCOUNT_UNAVAILABLE);
        }
        return issueInFamily(token.getUserId(), token.getFamily(), now);
    }

    /**
     * 이 기기의 세션(family)만 폐기합니다. 다른 기기의 세션은 그대로입니다.
     * 이미 로그아웃했거나 쿠키가 깨졌어도 오류 없이 끝나서, 로그아웃 버튼을 여러 번 눌러도 안전합니다.
     */
    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(new RawRefreshToken(rawRefreshToken).hash())
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamily(), now()));
    }

    /** 새 Refresh Token(원문은 응답 쿠키로, 해시는 DB로)과 Access Token을 만듭니다. */
    private IssuedTokens issueInFamily(Long userId, String family, OffsetDateTime now) {
        RawRefreshToken raw = RawRefreshToken.generate();
        refreshTokens.save(RefreshToken.issue(userId, raw.hash(), family, now, properties.ttl()));
        return new IssuedTokens(tokenIssuer.issueAccessToken(userId), raw.value());
    }

    /** 세션(family) 전체를 폐기하고, 던질 예외를 돌려줍니다. (호출하는 쪽에서 throw) */
    private BusinessException revokeSession(RefreshToken token, OffsetDateTime now, AuthErrorCode reason) {
        refreshTokens.revokeFamily(token.getFamily(), now);
        return new BusinessException(reason);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
