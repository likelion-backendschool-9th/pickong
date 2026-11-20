package com.pickong.feed.adapter.out.persistence.repository;

import com.pickong.feed.adapter.out.persistence.entity.FeedPhotoJpaEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedPhotoJpaRepository extends JpaRepository<FeedPhotoJpaEntity, Long> {
    List<FeedPhotoJpaEntity> findAllByFeedIdInOrderBySortOrder(Collection<Long> feedIds);

    void deleteAllByFeedId(Long feedId);
}
