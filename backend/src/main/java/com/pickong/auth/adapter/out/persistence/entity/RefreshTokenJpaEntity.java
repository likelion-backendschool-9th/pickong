package com.pickong.auth.adapter.out.persistence.entity;

import com.pickong.auth.domain.model.RefreshToken;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * refresh_tokens 테이블 JPA 엔티티.
 * JWT Refresh Token을 저장하며, family 단위 재사용 감지를 지원한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    /** 같은 family의 토큰이 재사용되면 family 전체를 폐기한다. */
    @Column(name = "family", nullable = false)
    private String family;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** domain 모델을 저장할 행으로 바꿉니다. (새 토큰이면 id는 null → DB가 채움) */
    public static RefreshTokenJpaEntity from(RefreshToken token) {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.id = token.getId();
        entity.userId = token.getUserId();
        entity.tokenHash = token.getTokenHash();
        entity.family = token.getFamily();
        entity.expiresAt = token.getExpiresAt();
        entity.revokedAt = token.getRevokedAt();
        entity.createdAt = token.getCreatedAt();
        return entity;
    }

    /** DB 행을 domain 모델로 바꿉니다. */
    public RefreshToken toDomain() {
        return RefreshToken.builder()
                .id(id)
                .userId(userId)
                .tokenHash(tokenHash)
                .family(family)
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .createdAt(createdAt)
                .build();
    }
}
