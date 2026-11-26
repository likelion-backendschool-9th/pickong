package com.pickong.savedplace.application.service;

import com.pickong.savedplace.application.dto.SavedPlaceResult;
import com.pickong.savedplace.application.port.in.SavedPlaceQueryUseCase;
import com.pickong.savedplace.application.port.out.SavedPlaceQueryPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SavedPlaceQueryService implements SavedPlaceQueryUseCase {

    private final SavedPlaceQueryPort savedPlaces;

    public SavedPlaceQueryService(SavedPlaceQueryPort savedPlaces) {
        this.savedPlaces = savedPlaces;
    }

    @Override
    public List<SavedPlaceResult> myList(long userId) {
        return savedPlaces.findByUserId(userId);
    }
}
