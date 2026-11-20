package com.pickong.feed.adapter.out.persistence;

import com.pickong.feed.adapter.out.persistence.entity.FeedEntity;
import com.pickong.feed.adapter.out.persistence.repository.FeedJpaRepository;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Feed;
import java.util.List;
import org.springframework.data.domain.PageRequest;

public class JpaFeedAdapter implements FeedRepository {
    private final FeedJpaRepository feedJpaRepository;

    public JpaFeedAdapter(FeedJpaRepository feedJpaRepository) {
        this.feedJpaRepository = feedJpaRepository;
    }

    @Override
    public Feed save(Feed feed) {
        FeedEntity entity = FeedEntity.from(feed);
        return feedJpaRepository.save(entity).toDomain();
    }

    @Override
    public List<Feed> findLatestFeeds(int limit) {
        return feedJpaRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(FeedEntity::toDomain).toList();
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
        return feedJpaRepository.findById(feedId)
                .orElseThrow(() -> new RuntimeException("피드를 찾을 수 없습니다."))
                .toDomain();
    }

    @Override
    public void deleteById(Long feedId) {
        feedJpaRepository.deleteById(feedId);
    }

}
