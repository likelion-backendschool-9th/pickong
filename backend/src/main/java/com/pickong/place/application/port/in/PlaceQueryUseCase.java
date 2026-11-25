package com.pickong.place.application.port.in;

import com.pickong.place.application.dto.PlaceMapItem;
import com.pickong.place.application.dto.PlaceMapQuery;
import com.pickong.place.application.dto.PlaceMapResult;
import com.pickong.place.domain.model.Place;
import java.util.List;

/**
 * 공개 장소 조회. places 행만 읽습니다.
 * 대기 · 반려 제보는 장소가 아니므로 포함되지 않습니다. 검색 · 상세 · 영업시간은 이 조회에 없습니다.
 */
public interface PlaceQueryUseCase {

    List<Place> findPublic();

    /** 지도 범위 안의 공개 장소. 가까이 있거나 좌표가 같은 장소는 묶습니다. */
    PlaceMapResult findMap(PlaceMapQuery query);

    /** 지도 범위 안의 공개 장소 목록. 묶지 않고 이름순으로 최대 개수까지만 돌려줍니다. */
    List<PlaceMapItem> findInBounds(PlaceMapQuery query);
}
