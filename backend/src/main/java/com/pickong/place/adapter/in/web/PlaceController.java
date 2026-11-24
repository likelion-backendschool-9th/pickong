package com.pickong.place.adapter.in.web;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.place.adapter.in.web.response.PublicPlaceResponse;
import com.pickong.place.application.port.in.PlaceQueryUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 장소 조회. places만 반환합니다.
 * bbox는 서경,남위,동경,북위 순서입니다. 없으면 전체입니다. POST /places는 만들지 않습니다.
 */
@RestController
@RequestMapping("/places")
public class PlaceController {

    private final PlaceQueryUseCase places;

    public PlaceController(PlaceQueryUseCase places) {
        this.places = places;
    }

    @GetMapping
    public List<PublicPlaceResponse> list(@RequestParam(required = false) String bbox) {
        if (bbox == null || bbox.isBlank()) {
            return places.findPublic().stream().map(PublicPlaceResponse::from).toList();
        }
        double[] box = bounds(bbox);
        return places.findInBounds(box[0], box[1], box[2], box[3]).stream().map(PublicPlaceResponse::from).toList();
    }

    private static double[] bounds(String bbox) {
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        try {
            return new double[] {
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[3].trim())
            };
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
