package com.pickong.feed.adapter.out.persistence.repository;

import com.pickong.feed.adapter.out.persistence.entity.FeedPhotoJpaEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedPhotoJpaRepository extends JpaRepository<FeedPhotoJpaEntity, Long> {
    List<FeedPhotoJpaEntity> findAllByFeedIdInOrderBySortOrder(Collection<Long> feedIds);

    void deleteAllByFeedId(Long feedId);

    // 수정 중인 게시물(exceptFeedId)의 사진은 빼고 봄. 새 게시물이면 exceptFeedId가 null이라 모든 게시물을 봄
    @Query("select count(p) > 0 from FeedPhotoJpaEntity p where p.mediaFileId in :mediaFileIds "
            + "and (:exceptFeedId is null or p.feedId <> :exceptFeedId)")
    boolean existsInOtherFeed(@Param("mediaFileIds") Collection<Long> mediaFileIds, @Param("exceptFeedId") Long exceptFeedId);
}
