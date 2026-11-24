package com.pickong.feed.adapter.out.persistence;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.ErrorCode;
import com.pickong.feed.adapter.out.persistence.entity.FeedJpaEntity;
import com.pickong.feed.adapter.out.persistence.entity.FeedPhotoJpaEntity;
import com.pickong.feed.adapter.out.persistence.entity.FeedPlaceJpaEntity;
import com.pickong.feed.adapter.out.persistence.repository.FeedJpaRepository;
import com.pickong.feed.adapter.out.persistence.repository.FeedPhotoJpaRepository;
import com.pickong.feed.adapter.out.persistence.repository.FeedPlaceJpaRepository;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Feed;
import com.pickong.feed.domain.model.FeedPhoto;
import com.pickong.feed.domain.model.FeedPlace;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaFeedAdapter implements FeedRepository {
    private final FeedJpaRepository feedJpaRepository;
    private final FeedPhotoJpaRepository feedPhotoJpaRepository;
    private final FeedPlaceJpaRepository feedPlaceJpaRepository;

    public JpaFeedAdapter(FeedJpaRepository feedJpaRepository,
            FeedPhotoJpaRepository feedPhotoJpaRepository,
            FeedPlaceJpaRepository feedPlaceJpaRepository) {
        this.feedJpaRepository = feedJpaRepository;
        this.feedPhotoJpaRepository = feedPhotoJpaRepository;
        this.feedPlaceJpaRepository = feedPlaceJpaRepository;
    }

    @Override
    @Transactional
    public Feed save(Feed feed) {
        FeedJpaEntity saved = feedJpaRepository.save(FeedJpaEntity.from(feed));
        Long feedId = saved.getId();

        feedPhotoJpaRepository.deleteAllByFeedId(feedId);
        feedPlaceJpaRepository.deleteAllByFeedId(feedId);
        List<FeedPhoto> photos = feedPhotoJpaRepository.saveAll(feed.getPhotos().stream()
                .map(photo -> FeedPhotoJpaEntity.of(feedId, photo))
                .toList())
                .stream().map(FeedPhotoJpaEntity::toDomain).toList();
        List<FeedPlace> places = feedPlaceJpaRepository.saveAll(feed.getPlaces().stream()
                .map(place -> FeedPlaceJpaEntity.of(feedId, place))
                .toList())
                .stream().map(FeedPlaceJpaEntity::toDomain).toList();

        return saved.toDomain(photos, places);
    }

    @Override
    public List<Feed> findRecommendedFeeds(Long userId, Long cursor, int limit) {
        // TODO: 사용자 활동 기반 추천 피드 조회 쿼리 (cursor 기반)
        return List.of();
    }

    @Override
    public List<Feed> findFollowingFeeds(Long userId, Long cursor, int limit) {
        // TODO: 팔로잉한 사용자의 피드 조회 쿼리 (cursor 기반)
        return List.of();
    }

    @Override
    public Feed findById(Long feedId) {
        FeedJpaEntity entity = feedJpaRepository.findById(feedId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEED_NOT_FOUND));
        return toDomain(List.of(entity)).getFirst();
    }

    @Override
    @Transactional
    public void deleteById(Long feedId) {
        // 존재하지 않는 feedId 삭제 시 404 반환
        if (!feedJpaRepository.existsById(feedId)) {
            throw new BusinessException(ErrorCode.FEED_NOT_FOUND);
        }
        feedPhotoJpaRepository.deleteAllByFeedId(feedId);
        feedPlaceJpaRepository.deleteAllByFeedId(feedId);
        feedJpaRepository.deleteById(feedId);
    }

    @Override
    public void incrementLikeCount(Long feedId) {
        feedJpaRepository.incrementLikeCount(feedId);
    }

    @Override
    public void decrementLikeCount(Long feedId) {
        feedJpaRepository.decrementLikeCount(feedId);
    }

    @Override
    public void incrementBookmarkCount(Long feedId) {
        feedJpaRepository.incrementBookmarkCount(feedId);
    }

    @Override
    public void decrementBookmarkCount(Long feedId) {
        feedJpaRepository.decrementBookmarkCount(feedId);
    }

    /** 사진 · 태그 장소를 게시물 id 묶음으로 한 번에 읽어 N+1 조회를 피합니다. */
    private List<Feed> toDomain(List<FeedJpaEntity> feeds) {
        if (feeds.isEmpty()) {
            return List.of();
        }
        List<Long> feedIds = feeds.stream().map(FeedJpaEntity::getId).toList();
        Map<Long, List<FeedPhoto>> photos = feedPhotoJpaRepository.findAllByFeedIdInOrderBySortOrder(feedIds).stream()
                .collect(Collectors.groupingBy(FeedPhotoJpaEntity::getFeedId,
                        Collectors.mapping(FeedPhotoJpaEntity::toDomain, Collectors.toList())));
        Map<Long, List<FeedPlace>> places = feedPlaceJpaRepository.findAllByFeedIdIn(feedIds).stream()
                .collect(Collectors.groupingBy(FeedPlaceJpaEntity::getFeedId,
                        Collectors.mapping(FeedPlaceJpaEntity::toDomain, Collectors.toList())));
        return feeds.stream()
                .map(feed -> feed.toDomain(
                        photos.getOrDefault(feed.getId(), List.of()),
                        places.getOrDefault(feed.getId(), List.of())))
                .toList();
    }
}
