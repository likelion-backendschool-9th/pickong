package com.pickong.savedplace.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /**
     * 회원이 출처로 연결할 수 있는 게시물. 삭제 · 숨김이 아니고 공개 범위상 열람할 수 있어야 합니다
     * (Feed.ensureAcceptsInteraction과 같은 기준). 없거나 볼 수 없으면 비어 있습니다.
     */
    Optional<SourceRef> findLinkableFeed(long userId, UUID feedPublicId);

    /**
     * 회원이 출처로 연결할 수 있는 컬렉션. 삭제 · 숨김이 아니고 공개 범위상 열람할 수 있어야 합니다.
     * 유료 컬렉션은 구매 기록(collection_purchases)이 생기기 전까지 본인 것만 연결합니다.
     */
    Optional<SourceRef> findLinkableCollection(long userId, UUID collectionPublicId);

    /**
     * 출처의 내부 id와, 그 출처에 실제로 들어 있는 장소들의 내부 id.
     * 출처에 없는 장소에는 출처 id를 연결하지 않습니다.
     */
    record SourceRef(long id, Set<Long> placeIds) {

        public SourceRef {
            placeIds = Set.copyOf(placeIds);
        }

        public boolean contains(long placeId) {
            return placeIds.contains(placeId);
        }
    }
}
