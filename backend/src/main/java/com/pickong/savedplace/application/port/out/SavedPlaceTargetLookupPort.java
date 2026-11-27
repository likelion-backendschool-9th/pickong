package com.pickong.savedplace.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 저장할 대상(장소)과 출처(게시물 · 컬렉션)의 공개 id를 내부 id로 바꿉니다.
 * places · feeds · collections는 다른 모듈 테이블이라 구현은 조회 어댑터의 읽기 전용 SQL입니다 (backend_blueprint.md 6.1).
 */
public interface SavedPlaceTargetLookupPort {

    /**
     * 회원이 지금 볼 수 있는 장소의 공개 id → 내부 id.
     * 삭제된 장소와 다른 회원의 비공개 장소는 빠집니다.
     */
    Map<UUID, Long> findVisiblePlaceIds(long userId, Collection<UUID> placePublicIds);

    /** 게시물 공개 id → 내부 id. 삭제된 게시물이면 비어 있습니다. */
    Optional<Long> findFeedId(UUID feedPublicId);

    /** 컬렉션 공개 id → 내부 id. 삭제된 컬렉션이면 비어 있습니다. */
    Optional<Long> findCollectionId(UUID collectionPublicId);
}
