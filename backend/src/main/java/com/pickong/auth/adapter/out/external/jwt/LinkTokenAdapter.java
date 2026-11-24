package com.pickong.auth.adapter.out.external.jwt;

import com.pickong.auth.adapter.out.external.jwt.JwtCodec.TokenType;
import com.pickong.auth.application.port.out.LinkTokenPort;
import com.pickong.auth.domain.vo.SocialProvider;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 계정 연결 토큰(JWT, token_type=link, 5분). sub=찜콩 회원 id, provider=연결할 제공자.
 * <p>
 * 주소(?link=)에 실려 브라우저 기록에 남을 수 있지만, 쓰려면 <b>같은 회원의 로그인 쿠키</b>가 함께 있어야 해서
 * 토큰만 새어 나가서는 남의 계정에 연결할 수 없습니다. 그래서 1회용으로 서버에 기록해 두지 않고 짧은 만료로만 제한합니다.
 */
@Component
public class LinkTokenAdapter implements LinkTokenPort {

    private final JwtCodec codec;

    public LinkTokenAdapter(JwtCodec codec) {
        this.codec = codec;
    }

    @Override
    public String issue(Long userId, SocialProvider provider) {
        return codec.encode(TokenType.LINK, String.valueOf(userId), Map.of("provider", provider.name()), TTL);
    }

    /** 서명 · 만료 · 종류(link)에 더해, 카카오용 토큰으로 Google 연결을 시작하지 못하게 제공자도 확인합니다. */
    @Override
    public Optional<Long> verify(String linkToken, SocialProvider provider) {
        return codec.decode(TokenType.LINK, linkToken)
                .filter(jwt -> provider.name().equals(jwt.getClaimAsString("provider")))
                .flatMap(jwt -> parseUserId(jwt.getSubject()));
    }

    private static Optional<Long> parseUserId(String subject) {
        try {
            return Optional.of(Long.valueOf(subject));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
