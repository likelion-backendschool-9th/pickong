package com.pickong.feed.adapter.out.persistence.repository;

import com.pickong.feed.adapter.out.persistence.entity.FeedPlaceJpaEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedPlaceJpaRepository extends JpaRepository<FeedPlaceJpaEntity, Long> {
    List<FeedPlaceJpaEntity> findAllByFeedIdIn(Collection<Long> feedIds);

    void deleteAllByFeedId(Long feedId);
}
