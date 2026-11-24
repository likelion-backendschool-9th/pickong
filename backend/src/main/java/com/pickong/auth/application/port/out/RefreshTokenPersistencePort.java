package com.pickong.auth.application.port.out;

import com.pickong.auth.domain.model.RefreshToken;
import java.time.OffsetDateTime;
import java.util.Optional;

/** Refresh Token 저장소 포트. 구현: {@code auth.adapter.out.persistence.RefreshTokenPersistenceAdapter} */
public interface RefreshTokenPersistencePort {

    RefreshToken save(RefreshToken token);

    /** 쿠키로 받은 토큰의 해시로 찾습니다. (원문은 DB에 없음) */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 아직 폐기되지 않았을 때만 폐기합니다. (조건부 UPDATE: WHERE revoked_at IS NULL)
     * <p>
     * 같은 토큰으로 동시에 두 요청이 와도 하나만 true를 받습니다.
     * 둘 다 '쓸 수 있다'고 읽고 각자 새 토큰을 만드는 일을 DB 수준에서 막기 위해서입니다.
     *
     * @return 이번 호출이 폐기했으면 true, 이미 다른 요청이 폐기했으면 false
     */
    boolean revokeIfActive(Long tokenId, OffsetDateTime now);

    /** 같은 family(그 기기의 로그인 세션)에서 아직 살아 있는 토큰을 모두 폐기합니다. */
    void revokeFamily(String family, OffsetDateTime now);

    /**
     * 만료 시각이 기준보다 이전인 토큰 행을 폐기 여부와 관계없이 지웁니다. (정리 작업 전용)
     *
     * @return 지운 행 수
     */
    int deleteExpiredBefore(OffsetDateTime threshold);
}
