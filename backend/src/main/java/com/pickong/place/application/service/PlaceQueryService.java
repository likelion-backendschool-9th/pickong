package com.pickong.place.application.service;

import com.pickong.common.domain.GeoPoint;
import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.place.application.port.in.PlaceQueryUseCase;
import com.pickong.place.application.port.out.PlacePersistencePort;
import com.pickong.place.domain.PlaceErrorCode;
import com.pickong.place.domain.model.Place;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlaceQueryService implements PlaceQueryUseCase {

    private final PlacePersistencePort places;

    public PlaceQueryService(PlacePersistencePort places) {
        this.places = places;
    }

    @Override
    public List<Place> findPublic() {
        return places.findAll();
    }

    @Override
    public List<Place> findInBounds(double west, double south, double east, double north) {
        try {
            new GeoPoint(south, west);
            new GeoPoint(north, east);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(PlaceErrorCode.INVALID_COORDINATES);
        }
        if (south > north || west > east) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return places.findInBounds(west, south, east, north);
    }
}
