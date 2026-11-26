package com.pickong.feed.application.port.in;

import com.pickong.feed.application.dto.AdminFeedView;
import java.util.List;

/** 관리자 게시물 목록. 상세 조회·컬렉션·상태 변경은 현재 범위가 아닙니다. */
public interface FeedAdminQueryUseCase {
    List<AdminFeedView> list(String query);
}
