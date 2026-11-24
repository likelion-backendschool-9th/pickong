package com.pickong.place.adapter.in.web.admin;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.common.security.CurrentAdmin;
import com.pickong.place.application.port.in.PlaceSubmissionUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 장소 제보 조회.
 * <p>
 * 목록은 배열 전체입니다. 상태 집계 · 검색 · 정렬은 화면이 하므로 page, size, totalCount를 보내지 않습니다.
 * 제보 id는 관리자 전용 숫자 id를 문자열로 보내고, 승인된 placeId는 장소 공개 id입니다.
 * 심사자 id는 {@link AdminPrincipal}에서만 읽습니다. 회원 토큰과 요청 본문의 id는 쓰지 않습니다.
 * POST는 기존 CSRF 검사를 그대로 탑니다. 토큰이 없으면 403 INVALID_CSRF_TOKEN입니다.
 */
@RestController
@RequestMapping("/admin/place-submissions")
public class AdminPlaceSubmissionController {

    private final PlaceSubmissionUseCase placeSubmissions;

    public AdminPlaceSubmissionController(PlaceSubmissionUseCase placeSubmissions) {
        this.placeSubmissions = placeSubmissions;
    }

    @GetMapping
    public List<PlaceSubmissionResponse> list(@CurrentAdmin AdminPrincipal admin) {
        return placeSubmissions.list().stream().map(PlaceSubmissionResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PlaceSubmissionResponse detail(@CurrentAdmin AdminPrincipal admin, @PathVariable String id) {
        return PlaceSubmissionResponse.from(placeSubmissions.get(submissionId(id)));
    }

    @PostMapping("/{id}/approve")
    public PlaceSubmissionResponse approve(@CurrentAdmin AdminPrincipal admin, @PathVariable String id) {
        return PlaceSubmissionResponse.from(placeSubmissions.approve(submissionId(id), admin.adminId()));
    }

    @PostMapping("/{id}/reject")
    public PlaceSubmissionResponse reject(@CurrentAdmin AdminPrincipal admin, @PathVariable String id,
            @RequestBody(required = false) RejectPlaceSubmissionRequest request) {
        String reason = request == null ? null : request.reason();
        return PlaceSubmissionResponse.from(placeSubmissions.reject(submissionId(id), admin.adminId(), reason));
    }

    private static long submissionId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
