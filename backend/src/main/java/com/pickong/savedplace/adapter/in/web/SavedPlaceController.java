package com.pickong.savedplace.adapter.in.web;

import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.savedplace.adapter.in.web.response.SavedPlaceResponse;
import com.pickong.savedplace.application.port.in.SavedPlaceQueryUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 저장 장소(찜) API. 로그인이 필요하고 본인이 저장한 장소만 다룹니다. */
@RestController
@RequestMapping("/saved-places")
public class SavedPlaceController {

    private final SavedPlaceQueryUseCase savedPlaces;

    public SavedPlaceController(SavedPlaceQueryUseCase savedPlaces) {
        this.savedPlaces = savedPlaces;
    }

    /** GET /api/v1/saved-places — 내 저장 장소 전체 (최근 저장순) */
    @GetMapping
    public List<SavedPlaceResponse> myList(@CurrentUser LoginUser user) {
        return savedPlaces.myList(user.userId()).stream().map(SavedPlaceResponse::from).toList();
    }
}
