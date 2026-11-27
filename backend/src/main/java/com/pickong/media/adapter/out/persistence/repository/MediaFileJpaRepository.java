package com.pickong.media.adapter.out.persistence.repository;

import com.pickong.media.adapter.out.persistence.entity.MediaFileJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaFileJpaRepository extends JpaRepository<MediaFileJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaFileJpaEntity m where m.id = :id")
    Optional<MediaFileJpaEntity> findByIdForUpdate(@Param("id") Long id);
}
