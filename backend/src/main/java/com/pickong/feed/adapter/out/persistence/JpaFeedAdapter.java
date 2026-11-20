package com.pickong.feed.adapter.out.persistence;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

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
    public List<Feed> findLatestFeeds(int limit) {
        return toDomain(feedJpaRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)));
    }

    @Override
    public List<Feed> findRecommendedFeeds(Long userId, int limit) {
        // TODO: 사용자 활동 기반 추천 피드 조회 쿼리
        return List.of();
    }

    @Override
    public List<Feed> findFollowingFeeds(Long userId, int limit) {
        // TODO: 팔로잉한 사용자의 피드 조회 쿼리
        return List.of();
    }

    @Override
    public List<Feed> findFeedsByTag(Long userId, List<String> tags, int limit) {
        // TODO: 태그 기반 피드 조회 쿼리
        return List.of();
    }

    @Override
    public Feed findById(Long feedId) {
        FeedJpaEntity entity = feedJpaRepository.findById(feedId)
                .orElseThrow(() -> new RuntimeException("피드를 찾을 수 없습니다."));
        return toDomain(List.of(entity)).getFirst();
    }

    @Override
    @Transactional
    public void deleteById(Long feedId) {
        feedPhotoJpaRepository.deleteAllByFeedId(feedId);
        feedPlaceJpaRepository.deleteAllByFeedId(feedId);
        feedJpaRepository.deleteById(feedId);
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
