package com.pickong.auth.adapter.out.persistence;

import com.pickong.auth.adapter.out.persistence.entity.RefreshTokenJpaEntity;
import com.pickong.auth.adapter.out.persistence.repository.RefreshTokenJpaRepository;
import com.pickong.auth.application.port.out.RefreshTokenPersistencePort;
import com.pickong.auth.domain.model.RefreshToken;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token 저장소. JPA 엔티티는 이 어댑터 밖으로 내보내지 않습니다.
 * 쓰기 메서드의 @Transactional은 서비스의 트랜잭션이 있으면 거기에 합류하고, 없으면 자체 트랜잭션으로 실행됩니다.
 */
@Component
public class RefreshTokenPersistenceAdapter implements RefreshTokenPersistencePort {

    private final RefreshTokenJpaRepository repository;

    public RefreshTokenPersistenceAdapter(RefreshTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        return repository.save(RefreshTokenJpaEntity.from(token)).toDomain();
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(RefreshTokenJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public boolean revokeIfActive(Long tokenId, OffsetDateTime now) {
        return repository.revokeIfActive(tokenId, now) == 1;
    }

    @Override
    @Transactional
    public void revokeFamily(String family, OffsetDateTime now) {
        repository.revokeFamily(family, now);
    }
}
