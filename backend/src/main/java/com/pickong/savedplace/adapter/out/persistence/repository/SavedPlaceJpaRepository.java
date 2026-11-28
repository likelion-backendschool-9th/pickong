package com.pickong.savedplace.adapter.out.persistence.repository;

import com.pickong.savedplace.adapter.out.persistence.entity.SavedPlaceJpaEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedPlaceJpaRepository extends JpaRepository<SavedPlaceJpaEntity, Long> {

    List<SavedPlaceJpaEntity> findByUserIdAndIdIn(Long userId, Collection<Long> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SavedPlaceJpaEntity s WHERE s.userId = :userId AND s.id IN :ids")
    int deleteMine(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);
}
