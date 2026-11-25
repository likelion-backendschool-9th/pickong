package com.pickong.place.adapter.in.web;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.place.adapter.in.web.response.PublicPlaceMapResponse;
import com.pickong.place.adapter.in.web.response.PublicPlaceResponse;
import com.pickong.place.application.dto.PlaceMapQuery;
import com.pickong.place.application.port.in.PlaceQueryUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 장소 조회. places만 반환합니다.
 * bbox는 서경,남위,동경,북위 순서입니다. 없으면 전체입니다. POST /places는 만들지 않습니다.
 * bbox가 있으면 가까이 있거나 좌표가 같은 장소를 묶고, cluster=false면 묶지 않은 목록을 돌려줍니다.
 */
@RestController
@RequestMapping("/places")
public class PlaceController {

    private final PlaceQueryUseCase places;

    public PlaceController(PlaceQueryUseCase places) {
        this.places = places;
    }

    @GetMapping
    public PublicPlaceMapResponse list(
            @RequestParam(required = false) String bbox,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "true") boolean cluster) {
        if (bbox == null || bbox.isBlank()) {
            return PublicPlaceMapResponse.of(places.findPublic().stream().map(PublicPlaceResponse::from).toList());
        }
        double[] box = bounds(bbox);
        PlaceMapQuery query = new PlaceMapQuery(box[0], box[1], box[2], box[3], category);
        return cluster
                ? PublicPlaceMapResponse.from(places.findMap(query))
                : PublicPlaceMapResponse.ofItems(places.findInBounds(query));
    }

    private static double[] bounds(String bbox) {
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        double[] values = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                values[i] = Double.parseDouble(parts[i].trim());
            }
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        // "NaN" · "Infinity"도 숫자로 읽히므로 따로 거릅니다.
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
        }
        return values;
    }
}
