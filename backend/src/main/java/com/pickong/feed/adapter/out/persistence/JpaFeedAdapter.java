package com.pickong.feed.adapter.out.persistence;

import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
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
import com.pickong.common.domain.Visibility;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class JpaFeedAdapter implements FeedRepository {
    @PersistenceContext
    private EntityManager entityManager;
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
        // IDENTITY 삽입보다 기존 관계 삭제를 먼저 반영해 같은 장소 재등록의 UNIQUE 충돌을 막습니다.
        feedJpaRepository.flush();
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
    public List<Feed> findRecommendedFeeds(Long viewerId, Long cursor, int limit) {
        return toDomain(feedJpaRepository.findRecommendedFeeds(viewerId, cursor, PageRequest.of(0, limit)));
    }

    @Override
    public List<Feed> findFollowingFeeds(Long viewerId, List<Long> followeeIds, Long cursor, int limit) {
        // 비로그인이면 볼 팔로잉 피드가 없음
        if (viewerId == null) {
            return List.of();
        }
        // 아무도 팔로우하지 않으면 빈 IN 조건을 만들 수 없어서 내 글만 조회 (본인은 모든 공개 범위)
        if (followeeIds == null || followeeIds.isEmpty()) {
            return toDomain(feedJpaRepository.findByOwnerAndVisibilities(
                    viewerId, names(EnumSet.allOf(Visibility.class)), cursor, PageRequest.of(0, limit)));
        }
        return toDomain(feedJpaRepository.findFollowingFeeds(viewerId, followeeIds, cursor, PageRequest.of(0, limit)));
    }

    @Override
    public Feed findById(Long feedId) {
        FeedJpaEntity entity = feedJpaRepository.findById(feedId)
                .orElseThrow(() -> new BusinessException(FeedErrorCode.FEED_NOT_FOUND));
        return toDomain(List.of(entity)).getFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Feed findByIdForUpdate(Long feedId) {
        return locked(feedJpaRepository.findByIdForUpdate(feedId)
                .orElseThrow(() -> new BusinessException(FeedErrorCode.FEED_NOT_FOUND)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Feed findByPublicIdForUpdate(UUID publicId) {
        return locked(feedJpaRepository.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new BusinessException(FeedErrorCode.FEED_NOT_FOUND)));
    }

    private Feed locked(FeedJpaEntity entity) {
        // 같은 트랜잭션에서 이미 읽은 행도 잠금 이후의 최신 상태로 판단합니다.
        entityManager.refresh(entity);
        return toDomain(List.of(entity)).getFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean updateHiddenAt(long feedId, OffsetDateTime previous, OffsetDateTime hiddenAt, OffsetDateTime now) {
        return feedJpaRepository.updateHiddenAt(feedId, previous, hiddenAt, now) == 1;
    }

    @Override
    @Transactional
    public void deleteById(Long feedId) {
        // 존재하지 않는 feedId 삭제 시 404 반환
        if (!feedJpaRepository.existsById(feedId)) {
            throw new BusinessException(FeedErrorCode.FEED_NOT_FOUND);
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

    @Override
    public List<Feed> findByOwner(Long ownerUserId, Set<Visibility> visibilities, Long cursor, int limit) {
        return toDomain(feedJpaRepository.findByOwnerAndVisibilities(
                ownerUserId, names(visibilities), cursor, PageRequest.of(0, limit)));
    }

    // feeds.visibility 컬럼은 문자열이라 enum 이름으로 바꿔서 넘김
    private static List<String> names(Set<Visibility> visibilities) {
        return visibilities.stream().map(Visibility::name).toList();
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
