package com.pickong.place.adapter.in.web;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.place.adapter.in.web.admin.PlaceSubmissionResponse;
import com.pickong.place.adapter.in.web.request.SubmitPlaceRequest;
import com.pickong.place.application.dto.SubmitPlaceCommand;
import com.pickong.place.application.port.in.PlaceSubmissionUseCase;
import com.pickong.place.domain.PlaceErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원 장소 제보. 승인 전에는 공개 장소를 만들지 않습니다. */
@RestController
@RequestMapping("/place-submissions")
public class PlaceSubmissionController {

    private final PlaceSubmissionUseCase placeSubmissions;

    public PlaceSubmissionController(PlaceSubmissionUseCase placeSubmissions) {
        this.placeSubmissions = placeSubmissions;
    }

    @PostMapping
    public ResponseEntity<PlaceSubmissionResponse> submit(@CurrentUser LoginUser user,
            @RequestBody(required = false) SubmitPlaceRequest request) {
        if (user.userId() == null || user.userId() <= 0) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        if (request == null || request.lat() == null || request.lng() == null) {
            throw new BusinessException(PlaceErrorCode.INVALID_COORDINATES);
        }
        var saved = placeSubmissions.submit(new SubmitPlaceCommand(
                user.userId(),
                request.name(),
                request.address(),
                request.category(),
                request.description(),
                request.imageUrl(),
                request.lat(),
                request.lng()));
        return ResponseEntity.status(HttpStatus.CREATED).body(PlaceSubmissionResponse.from(saved));
    }
}
