package com.pickong.place.application.service;

import com.pickong.common.domain.GeoPoint;
import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.place.application.dto.PlaceCluster;
import com.pickong.place.application.dto.PlaceMapGroup;
import com.pickong.place.application.dto.PlaceMapItem;
import com.pickong.place.application.dto.PlaceMapQuery;
import com.pickong.place.application.dto.PlaceMapResult;
import com.pickong.place.application.port.in.PlaceQueryUseCase;
import com.pickong.place.application.port.out.PlaceMapQueryPort;
import com.pickong.place.application.port.out.PlacePersistencePort;
import com.pickong.place.domain.PlaceErrorCode;
import com.pickong.place.domain.model.Place;
import com.pickong.place.domain.policy.MapBoundsPolicy;
import com.pickong.place.domain.policy.MapGridPolicy;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlaceQueryService implements PlaceQueryUseCase {

    /** 화면에 그대로 표시할 최대 좌표 수. 넘으면 격자로 묶습니다. */
    static final int MAX_SPOTS = 200;

    /** 묶지 않은 목록의 최대 장소 수. */
    static final int MAX_LIST = 500;

    private final PlacePersistencePort places;
    private final PlaceMapQueryPort placeMap;

    public PlaceQueryService(PlacePersistencePort places, PlaceMapQueryPort placeMap) {
        this.places = places;
        this.placeMap = placeMap;
    }

    @Override
    public List<Place> findPublic() {
        return places.findAll();
    }

    @Override
    public PlaceMapResult findMap(PlaceMapQuery query) {
        validate(query);
        List<PlaceMapGroup> groups = placeMap.groupBySpot(query, MAX_SPOTS + 1);
        if (groups.size() > MAX_SPOTS) {
            double cellSize = MapGridPolicy.cellSize(query.west(), query.south(), query.east(), query.north());
            groups = placeMap.groupByGrid(query, cellSize);
        }
        return toResult(groups);
    }

    @Override
    public List<PlaceMapItem> findInBounds(PlaceMapQuery query) {
        validate(query);
        return placeMap.list(query, MAX_LIST);
    }

    private static PlaceMapResult toResult(List<PlaceMapGroup> groups) {
        List<PlaceMapItem> singles = new ArrayList<>();
        List<PlaceCluster> clusters = new ArrayList<>();
        for (PlaceMapGroup group : groups) {
            if (group.single() != null) {
                singles.add(group.single());
            } else {
                clusters.add(group.cluster());
            }
        }
        return new PlaceMapResult(List.copyOf(singles), List.copyOf(clusters));
    }

    private static void validate(PlaceMapQuery query) {
        try {
            new GeoPoint(query.south(), query.west());
            new GeoPoint(query.north(), query.east());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(PlaceErrorCode.INVALID_COORDINATES);
        }
        if (query.south() > query.north() || query.west() > query.east()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        // 광역 범위는 묶어도 많은 행을 읽고, 목록(cluster=false)은 범위 전체를 정렬하므로 막습니다.
        if (MapBoundsPolicy.isTooLarge(query.west(), query.south(), query.east(), query.north())) {
            throw new BusinessException(PlaceErrorCode.BOUNDS_TOO_LARGE);
        }
    }
}
