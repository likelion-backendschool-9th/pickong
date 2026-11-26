package com.pickong.feed.application.service;

import com.pickong.feed.application.dto.AdminFeedView;
import com.pickong.feed.application.port.in.FeedAdminQueryUseCase;
import com.pickong.feed.application.port.out.AdminFeedQueryPort;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FeedAdminQueryService implements FeedAdminQueryUseCase {
    private final AdminFeedQueryPort feeds;

    public FeedAdminQueryService(AdminFeedQueryPort feeds) {
        this.feeds = feeds;
    }

    @Override
    public List<AdminFeedView> list(String query) {
        String search = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        // 현재 계약은 페이지 없는 전체 목록입니다. mock과 동일하게 제목·닉네임을 문자 그대로 검색합니다.
        return feeds.findAll().stream().filter(feed -> search.isEmpty()
                || (feed.title() + (feed.author() == null ? "" : feed.author().nickname()))
                        .toLowerCase(Locale.ROOT).contains(search)).toList();
    }
}
