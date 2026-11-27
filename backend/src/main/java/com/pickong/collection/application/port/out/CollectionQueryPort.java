package com.pickong.collection.application.port.out;

import com.pickong.collection.application.dto.CollectionSummary;
import com.pickong.common.domain.Visibility;
import java.util.List;
import java.util.Set;

/** 컬렉션 화면 조회. */
public interface CollectionQueryPort {

    /**
     * 소유자의 컬렉션 중 공개 범위가 visibilities에 들어가는 것. 삭제 · 숨김은 빼고 최신순입니다.
     *
     * @param viewerId 표지로 쓸 장소 사진을 고를 때, 이 회원이 볼 수 있는 장소만 씁니다 (직접 추가한 비공개 장소)
     */
    List<CollectionSummary> findByOwner(long ownerId, Set<Visibility> visibilities, long viewerId);
}
