package com.pickong.moderation.application.port.out;

import com.pickong.moderation.application.dto.AdminUserView;
import java.time.OffsetDateTime;
import java.util.List;

/** 사용자·콘텐츠·신고·제재를 읽기 전용으로 조합합니다. */
public interface AdminUserQueryPort {
    List<AdminUserView> findUsers(String query, OffsetDateTime now);
}
