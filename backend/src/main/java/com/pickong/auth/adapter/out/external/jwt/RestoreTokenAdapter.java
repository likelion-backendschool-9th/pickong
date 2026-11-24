package com.pickong.auth.adapter.out.external.jwt;

import com.pickong.auth.adapter.out.external.jwt.JwtCodec.TokenType;
import com.pickong.auth.application.port.out.RestoreTokenPort;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 탈퇴 취소 토큰 (token_type=withdrawal_restore). Access · 가입 토큰과 같은 키로 서명하지만 종류가 달라 서로 바꿔 쓸 수 없습니다. */
@Component
public class RestoreTokenAdapter implements RestoreTokenPort {

    private final JwtCodec codec;

    public RestoreTokenAdapter(JwtCodec codec) {
        this.codec = codec;
    }

    @Override
    public String issue(Long userId) {
        return codec.encode(TokenType.RESTORE, String.valueOf(userId), Map.of(), TTL);
    }

    @Override
    public Optional<Long> verify(String restoreToken) {
        if (restoreToken == null || restoreToken.isBlank()) {
            return Optional.empty();
        }
        return codec.decode(TokenType.RESTORE, restoreToken).flatMap(jwt -> {
            try {
                return Optional.of(Long.valueOf(jwt.getSubject()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }
}
