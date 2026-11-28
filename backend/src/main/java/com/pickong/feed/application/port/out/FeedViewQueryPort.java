package com.pickong.feed.application.port.out;

import com.pickong.feed.application.dto.FeedAuthor;
import com.pickong.feed.application.dto.FeedViewPlace;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 화면용 게시물 정보 조회 포트. users · media_files · places 등 다른 모듈 테이블을 읽음
 * 구현: feed.adapter.out.persistence.query.FeedViewQueryAdapter (blueprint 6.1)
 */
public interface FeedViewQueryPort {

    // 작성자 users.id → 요약
    Map<Long, FeedAuthor> findAuthors(Collection<Long> userIds);

    // 게시물 id → 업로드가 끝난 사진 주소 (sort_order 순서)
    Map<Long, List<String>> findPhotoUrls(Collection<Long> feedIds);

    // 게시물 id → 연결된 장소 (삭제되지 않았고, 공개이거나 보는 사람 소유인 장소만)
    Map<Long, List<FeedViewPlace>> findPlaces(Collection<Long> feedIds, Long viewerId);

    // 보는 사람이 좋아요한 게시물 id
    Set<Long> findLikedFeedIds(long viewerId, Collection<Long> feedIds);

    // 보는 사람이 북마크한 게시물 id
    Set<Long> findBookmarkedFeedIds(long viewerId, Collection<Long> feedIds);
}
