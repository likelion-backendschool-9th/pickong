package com.pickong.collection.application.port.in;

import com.pickong.collection.application.dto.UserCollectionsResult;

/**
 * 컬렉션 조회 유스케이스.
 * (blueprint 3장: GET /users/{username}/collections → collectionsOf. 내 목록 · 상세 · 탐색 · 버전은 아직 없습니다)
 */
public interface CollectionQueryUseCase {

    /**
     * 프로필의 컬렉션 탭. 본인은 공개 범위와 관계없이 모두, 다른 회원은 공개 범위가 허용하는 것만 최신순으로 돌려줍니다.
     * 삭제 · 관리자 숨김 컬렉션은 본인에게도 보이지 않습니다.
     *
     * @throws com.pickong.common.error.BusinessException 404 USER_NOT_FOUND — 없거나 정상(ACTIVE) 회원이 아닐 때 (프로필 조회와 같은 기준)
     */
    UserCollectionsResult collectionsOf(Long viewerId, String username);
}
