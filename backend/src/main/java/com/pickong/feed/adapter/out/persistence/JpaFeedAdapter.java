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
import java.util.List;
import java.util.Map;
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
    public List<Feed> findRecommendedFeeds(Long userId, Long cursor, int limit) {
        // userId는 추천 피드에서 사용 안 함 — PUBLIC 피드 최신순 조회
        List<FeedJpaEntity> entities = feedJpaRepository.findRecommendedFeeds(
                cursor, PageRequest.of(0, limit));
        return toDomain(entities);
    }

    @Override
    public List<Feed> findFollowingFeeds(Long userId, Long cursor, int limit) {
        // userId가 null이면(비로그인) 빈 리스트 반환
        if (userId == null) {
            return List.of();
        }
        List<FeedJpaEntity> entities = feedJpaRepository.findFollowingFeeds(
                userId, cursor, PageRequest.of(0, limit));
        return toDomain(entities);
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

    // 서비스가 행을 잠그고 소유자를 확인한 같은 트랜잭션에서만 부르게 함
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean softDelete(long feedId) {
        return feedJpaRepository.softDelete(feedId) == 1;
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
    public List<Feed> findByOwner(Long ownerUserId, boolean isOwner, Long cursor, int limit) {
        // 본인이면 모든 visibility, 타인이면 PUBLIC만 조회
        List<FeedJpaEntity> entities = isOwner
                ? feedJpaRepository.findAllByOwner(ownerUserId, cursor, PageRequest.of(0, limit))
                : feedJpaRepository.findPublicByOwner(ownerUserId, cursor, PageRequest.of(0, limit));
        return toDomain(entities);
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
