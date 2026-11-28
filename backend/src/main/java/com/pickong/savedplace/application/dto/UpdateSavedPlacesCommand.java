package com.pickong.savedplace.application.dto;

import com.pickong.savedplace.domain.vo.VisitStatus;
import java.util.List;

/**
 * 내 저장 장소 여러 곳의 방문 상태 · 메모를 바꿉니다. PATCH /saved-places. 보낸 값만 바꿉니다.
 *
 * @param ids         저장 장소 id. 본인 것이 아니거나 없는 id는 건너뜁니다
 * @param visitStatus null이면 그대로
 * @param memo        null이면 그대로, 빈 문자열이면 메모 없음
 */
public record UpdateSavedPlacesCommand(long userId, List<Long> ids, VisitStatus visitStatus, String memo) {

    public UpdateSavedPlacesCommand {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }
}
