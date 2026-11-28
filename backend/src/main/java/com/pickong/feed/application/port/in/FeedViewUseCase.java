package com.pickong.feed.application.port.in;

import com.pickong.feed.application.dto.FeedView;
import com.pickong.feed.domain.model.Feed;
import java.util.List;

/**
 * 조회한 게시물에 화면용 정보(작성자, 사진 주소, 장소, 좋아요 · 북마크 여부)를 붙임
 * 공개 범위 검사는 하지 않음. 이미 FeedUseCase가 볼 수 있다고 판단한 결과만 넘겨야 됨
 */
public interface FeedViewUseCase {

    // 넘긴 순서 그대로 돌려줌. 여러 건을 한 번에 조회해서 목록에서 게시물마다 쿼리가 나가지 않게 함
    List<FeedView> viewsOf(List<Feed> feeds, Long viewerId);

    default FeedView viewOf(Feed feed, Long viewerId) {
        return viewsOf(List.of(feed), viewerId).get(0);
    }
}
