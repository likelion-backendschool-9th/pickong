package com.pickong.moderation.application.port.in;

import com.pickong.moderation.application.dto.AdminUserView;
import java.util.List;

/** 관리자 사용자 제재 목록. 페이지네이션과 상세·이력 전용 조회는 현재 범위가 아닙니다. */
public interface UserAdminQueryUseCase {
    List<AdminUserView> list(String query, String filter);
}
