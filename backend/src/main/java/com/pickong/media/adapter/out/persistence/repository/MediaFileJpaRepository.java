package com.pickong.media.adapter.out.persistence.repository;

import com.pickong.media.adapter.out.persistence.entity.MediaFileJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaFileJpaRepository extends JpaRepository<MediaFileJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaFileJpaEntity m where m.id = :id")
    Optional<MediaFileJpaEntity> findByIdForUpdate(@Param("id") Long id);

    // id 순서로 잠가서, 두 요청이 같은 사진들을 서로 다른 순서로 잠그다 교착(deadlock)되지 않게 함
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaFileJpaEntity m where m.id in :ids order by m.id")
    List<MediaFileJpaEntity> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);
}
