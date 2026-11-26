package com.pickong.feed.application.port.out;

import com.pickong.feed.application.dto.AdminFeedView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminFeedQueryPort {
    /** 삭제되지 않은 게시물 전체. 생성 시각 내림차순, 동률이면 내부 id 내림차순입니다. */
    List<AdminFeedView> findAll();

    Optional<AdminFeedView> findByPublicId(UUID publicId);
}
