package com.pickong.media.adapter.out.persistence;

import com.pickong.media.adapter.out.persistence.entity.MediaFileJpaEntity;
import com.pickong.media.adapter.out.persistence.repository.MediaFileJpaRepository;
import com.pickong.media.application.port.out.MediaPersistencePort;
import com.pickong.media.domain.model.MediaFile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaMediaFileAdapter implements MediaPersistencePort {

    private final MediaFileJpaRepository mediaFileJpaRepository;

    public JpaMediaFileAdapter(MediaFileJpaRepository mediaFileJpaRepository) {
        this.mediaFileJpaRepository = mediaFileJpaRepository;
    }

    @Override
    public MediaFile save(MediaFile media) {
        return mediaFileJpaRepository.save(MediaFileJpaEntity.from(media)).toDomain();
    }

    // 잠금은 트랜잭션 안에서만 의미가 있어서 서비스 트랜잭션이 없으면 바로 오류를 냄
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<MediaFile> findByIdForUpdate(Long mediaId) {
        return mediaFileJpaRepository.findByIdForUpdate(mediaId).map(MediaFileJpaEntity::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<MediaFile> findAllByIdsForUpdate(Collection<Long> mediaIds) {
        return mediaFileJpaRepository.findAllByIdInForUpdate(mediaIds).stream().map(MediaFileJpaEntity::toDomain).toList();
    }
}
