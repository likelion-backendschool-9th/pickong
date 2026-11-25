package com.pickong.place.application.port.out;

import com.pickong.place.application.dto.PlaceMapGroup;
import com.pickong.place.application.dto.PlaceMapItem;
import com.pickong.place.application.dto.PlaceMapQuery;
import java.util.List;

/** 지도 범위 조회. 비공개 · 삭제된 장소는 제외합니다. */
public interface PlaceMapQueryPort {

    /** 같은 좌표끼리 묶습니다. 최대 limit개만 돌려줍니다. */
    List<PlaceMapGroup> groupBySpot(PlaceMapQuery query, int limit);

    /** 한 칸이 cellSize인 격자로 묶습니다. 크기는 MapGridPolicy가 정합니다. */
    List<PlaceMapGroup> groupByGrid(PlaceMapQuery query, double cellSize);

    /** 묶지 않은 장소 목록. 이름순으로 최대 limit개입니다. */
    List<PlaceMapItem> list(PlaceMapQuery query, int limit);
}
