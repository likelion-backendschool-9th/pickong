package com.pickong.place.application.port.out;

import com.pickong.place.domain.model.Place;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 공개 장소 저장. 제보 승인이 같은 트랜잭션에서 호출합니다. */
public interface PlacePersistencePort {

    /** 새 장소를 저장하고 DB id가 채워진 값을 돌려줍니다. */
    Place save(Place place);

    /** 내부 id에 해당하는 공개 id. 없는 id는 맵에 없습니다. */
    Map<Long, UUID> findPublicIdsByIds(Collection<Long> placeIds);

    /** places 행 전체. 대기 · 반려 제보는 이 테이블에 없습니다. */
    List<Place> findAll();

    /** 지도 범위 안의 places 행. 경도 west~east, 위도 south~north. */
    List<Place> findInBounds(double west, double south, double east, double north);
}
