package com.pickong.place.application.port.in;

import com.pickong.place.domain.model.Place;
import java.util.List;

/**
 * 공개 장소 조회. places 행만 읽습니다.
 * 대기 · 반려 제보는 장소가 아니므로 포함되지 않습니다. 검색 · 상세 · 영업시간은 이 조회에 없습니다.
 */
public interface PlaceQueryUseCase {

    List<Place> findPublic();

    /** 경도 west~east, 위도 south~north 안의 공개 장소. */
    List<Place> findInBounds(double west, double south, double east, double north);
}
