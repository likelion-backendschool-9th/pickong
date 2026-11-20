package com.pickong.feed.adapter.out.persistence.repository;

import com.pickong.feed.adapter.out.persistence.entity.FeedEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedJpaRepository extends JpaRepository<FeedEntity, Long> {
    List<FeedEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
