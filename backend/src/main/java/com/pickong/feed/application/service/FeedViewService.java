package com.pickong.feed.application.service;

import com.pickong.feed.application.dto.FeedAuthor;
import com.pickong.feed.application.dto.FeedView;
import com.pickong.feed.application.dto.FeedViewPlace;
import com.pickong.feed.application.port.in.FeedViewUseCase;
import com.pickong.feed.application.port.out.FeedViewQueryPort;
import com.pickong.feed.domain.model.Feed;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedViewService implements FeedViewUseCase {

    private final FeedViewQueryPort feedViewQueryPort;

    public FeedViewService(FeedViewQueryPort feedViewQueryPort) {
        this.feedViewQueryPort = feedViewQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedView> viewsOf(List<Feed> feeds, Long viewerId) {
        if (feeds.isEmpty()) {
            return List.of();
        }
        // 1. 게시물 id, 작성자 id 모으기 → 2. 종류별로 한 번씩 조회 → 3. 게시물마다 조립 (목록이어도 쿼리 수는 고정)
        List<Long> feedIds = feeds.stream().map(Feed::getId).toList();
        List<Long> authorIds = feeds.stream().map(Feed::getUserId).distinct().toList();
        Map<Long, FeedAuthor> authors = feedViewQueryPort.findAuthors(authorIds);
        Map<Long, List<String>> photoUrls = feedViewQueryPort.findPhotoUrls(feedIds);
        Map<Long, List<FeedViewPlace>> places = feedViewQueryPort.findPlaces(feedIds, viewerId);
        // 비로그인이면 좋아요 · 북마크 조회 없이 전부 false
        Set<Long> liked = viewerId == null ? Set.of() : feedViewQueryPort.findLikedFeedIds(viewerId, feedIds);
        Set<Long> bookmarked = viewerId == null ? Set.of() : feedViewQueryPort.findBookmarkedFeedIds(viewerId, feedIds);

        return feeds.stream()
                .map(feed -> new FeedView(
                        feed,
                        authors.getOrDefault(feed.getUserId(), FeedAuthor.unknown()),
                        photoUrls.getOrDefault(feed.getId(), List.of()),
                        places.getOrDefault(feed.getId(), List.of()),
                        liked.contains(feed.getId()),
                        bookmarked.contains(feed.getId())))
                .toList();
    }
}
