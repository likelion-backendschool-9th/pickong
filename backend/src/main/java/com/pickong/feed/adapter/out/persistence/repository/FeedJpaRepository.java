package com.pickong.feed.adapter.out.persistence.repository;

import com.pickong.feed.adapter.out.persistence.entity.FeedJpaEntity;
import com.pickong.feed.adapter.out.persistence.repository.FeedPlaceRow;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedJpaRepository extends JpaRepository<FeedJpaEntity, Long> {
    List<FeedJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // PUBLIC 피드를 최신순으로 조회 (삭제·숨김 제외, cursor 없으면 첫 페이지)
    @Query("SELECT f FROM FeedJpaEntity f " +
           "WHERE f.visibility = 'PUBLIC' " +
           "AND f.deletedAt IS NULL " +
           "AND f.hiddenAt IS NULL " +
           "AND (:cursor IS NULL OR f.id < :cursor) " +
           "ORDER BY f.id DESC")
    List<FeedJpaEntity> findRecommendedFeeds(
            @Param("cursor") Long cursor,
            Pageable pageable);

    // 팔로잉한 사용자의 PUBLIC 피드를 최신순으로 조회 (삭제·숨김 제외)
    @Query("SELECT f FROM FeedJpaEntity f " +
           "WHERE f.userId IN (SELECT fw.followeeId FROM FollowJpaEntity fw WHERE fw.followerId = :userId) " +
           "AND f.visibility = 'PUBLIC' " +
           "AND f.deletedAt IS NULL " +
           "AND f.hiddenAt IS NULL " +
           "AND (:cursor IS NULL OR f.id < :cursor) " +
           "ORDER BY f.id DESC")
    List<FeedJpaEntity> findFollowingFeeds(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // like_count를 DB에서 직접 +1 — 동시 요청에서도 카운트 정확하게 유지됨
    @Modifying
    @Query("UPDATE FeedJpaEntity f SET f.likeCount = f.likeCount + 1 WHERE f.id = :feedId")
    void incrementLikeCount(@Param("feedId") Long feedId);

    // like_count -1, 단 0 미만으로 내려가지 않도록 조건 추가
    @Modifying
    @Query("UPDATE FeedJpaEntity f SET f.likeCount = f.likeCount - 1 WHERE f.id = :feedId AND f.likeCount > 0")
    void decrementLikeCount(@Param("feedId") Long feedId);

    @Modifying
    @Query("UPDATE FeedJpaEntity f SET f.bookmarkCount = f.bookmarkCount + 1 WHERE f.id = :feedId")
    void incrementBookmarkCount(@Param("feedId") Long feedId);

    @Modifying
    @Query("UPDATE FeedJpaEntity f SET f.bookmarkCount = f.bookmarkCount - 1 WHERE f.id = :feedId AND f.bookmarkCount > 0")
    void decrementBookmarkCount(@Param("feedId") Long feedId);

    // 추천 피드 지도용: PUBLIC 피드에 연결된 장소 목록 (최대 500개 피드 기준)
    // places 테이블을 직접 JOIN해서 장소 상세 정보를 함께 가져옴 (N+1 방지)
    @Query(value = """
            SELECT
                p.id              AS placeId,
                p.public_id::text AS placePublicId,
                p.name            AS placeName,
                p.address         AS placeAddress,
                ST_Y(p.location::geometry) AS lat,
                ST_X(p.location::geometry) AS lng,
                p.category        AS category,
                p.source_type     AS sourceType,
                f.id              AS feedId
            FROM feeds f
            JOIN feed_places fp ON fp.feed_id = f.id
            JOIN places p       ON p.id = fp.place_id
            WHERE f.deleted_at IS NULL
              AND f.hidden_at IS NULL
              AND f.visibility = 'PUBLIC'
            ORDER BY f.created_at DESC
            LIMIT 500
            """, nativeQuery = true)
    List<FeedPlaceRow> findRecommendedFeedPlaces();

    // 팔로잉 피드 지도용: 팔로잉한 사용자의 PUBLIC 피드에 연결된 장소 목록
    @Query(value = """
            SELECT
                p.id              AS placeId,
                p.public_id::text AS placePublicId,
                p.name            AS placeName,
                p.address         AS placeAddress,
                ST_Y(p.location::geometry) AS lat,
                ST_X(p.location::geometry) AS lng,
                p.category        AS category,
                p.source_type     AS sourceType,
                f.id              AS feedId
            FROM feeds f
            JOIN feed_places fp ON fp.feed_id = f.id
            JOIN places p       ON p.id = fp.place_id
            WHERE f.deleted_at IS NULL
              AND f.hidden_at IS NULL
              AND f.visibility = 'PUBLIC'
              AND f.user_id IN (
                  SELECT fw.followee_id FROM follows fw WHERE fw.follower_id = :userId
              )
            ORDER BY f.created_at DESC
            LIMIT 500
            """, nativeQuery = true)
    List<FeedPlaceRow> findFollowingFeedPlaces(@Param("userId") Long userId);
}
