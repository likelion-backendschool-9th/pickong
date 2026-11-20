package com.pickong.feed.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface FeedJpaRepository extends JpaRepository<FeedEntity, Long> {
    List<FeedEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
