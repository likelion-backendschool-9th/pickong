package com.pickong.savedplace.adapter.in.web;

import com.pickong.common.error.BusinessException;
import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.savedplace.adapter.in.web.request.DeleteSavedPlacesRequest;
import com.pickong.savedplace.adapter.in.web.request.SaveSavedPlacesRequest;
import com.pickong.savedplace.adapter.in.web.request.UpdateSavedPlacesRequest;
import com.pickong.savedplace.adapter.in.web.response.SaveSavedPlacesResponse;
import com.pickong.savedplace.adapter.in.web.response.SavedPlaceResponse;
import com.pickong.savedplace.application.dto.SaveSavedPlacesCommand;
import com.pickong.savedplace.application.dto.UpdateSavedPlacesCommand;
import com.pickong.savedplace.application.port.in.SavedPlaceCommandUseCase;
import com.pickong.savedplace.application.port.in.SavedPlaceQueryUseCase;
import com.pickong.savedplace.domain.SavedPlaceErrorCode;
import com.pickong.savedplace.domain.vo.VisitStatus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 저장 장소(찜) API. 로그인이 필요하고 본인이 저장한 장소만 다룹니다.
 * 저장, 수정, 삭제는 여러 건을 한 번에 처리합니다 (backend_blueprint.md 4장 savedplace, 프론트 placeApi.ts와 같음).
 */
@RestController
@RequestMapping("/saved-places")
public class SavedPlaceController {

    private final SavedPlaceQueryUseCase savedPlaces;
    private final SavedPlaceCommandUseCase commands;

    public SavedPlaceController(SavedPlaceQueryUseCase savedPlaces, SavedPlaceCommandUseCase commands) {
        this.savedPlaces = savedPlaces;
        this.commands = commands;
    }

    /** GET /api/v1/saved-places — 내 저장 장소 전체 (최근 저장순) */
    @GetMapping
    public List<SavedPlaceResponse> myList(@CurrentUser LoginUser user) {
        return savedPlaces.myList(user.userId()).stream().map(SavedPlaceResponse::from).toList();
    }

    /** POST /api/v1/saved-places — 장소 여러 곳 저장. 이미 저장한 장소는 linkedPlaceIds로 돌려줍니다. */
    @PostMapping
    public SaveSavedPlacesResponse save(@CurrentUser LoginUser user,
                                        @RequestBody(required = false) SaveSavedPlacesRequest request) {
        SaveSavedPlacesRequest body = request == null ? new SaveSavedPlacesRequest(null, null, null) : request;
        rejectCollectionLink(body.collectionIds());
        SaveSavedPlacesRequest.Source source = body.source();
        SaveSavedPlacesCommand command = new SaveSavedPlacesCommand(
                user.userId(),
                placeIds(body.placeIds()),
                SourceTypes.fromApi(source == null ? null : source.type()),
                source == null ? null : source.refId(),
                source == null ? null : source.label());
        return SaveSavedPlacesResponse.from(commands.save(command));
    }

    /**
     * PATCH /api/v1/saved-places — 방문 상태 · 메모 수정. 바뀐 저장 장소를 돌려줍니다.
     * 메모: 생략하거나 null이면 그대로, 빈 문자열(공백만 포함)이면 지웁니다.
     */
    @PatchMapping
    public List<SavedPlaceResponse> update(@CurrentUser LoginUser user,
                                           @RequestBody(required = false) UpdateSavedPlacesRequest request) {
        if (request == null) {
            return List.of();
        }
        rejectCollectionLink(request.addCollectionIds(), request.setCollectionIds(), request.removeCollectionIds());
        UpdateSavedPlacesCommand command = new UpdateSavedPlacesCommand(
                user.userId(), savedPlaceIds(request.ids()), visitStatus(request.visitStatus()), request.memo());
        return commands.update(command).stream().map(SavedPlaceResponse::from).toList();
    }

    /** DELETE /api/v1/saved-places — 본문 {ids}의 저장 기록 삭제. 본인 것이 아닌 id는 건너뜁니다. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@CurrentUser LoginUser user,
                                       @RequestBody(required = false) DeleteSavedPlacesRequest request) {
        commands.delete(user.userId(), savedPlaceIds(request == null ? null : request.ids()));
        return ResponseEntity.noContent().build();
    }

    /**
     * 컬렉션에 담기는 아직 구현하지 않았습니다 (8.1 #13). 받고 무시하면 성공처럼 보이므로 값이 있으면 거절합니다.
     * 프론트는 컬렉션을 고르지 않아도 {@code collectionIds: []}를 보내므로 빈 목록은 받습니다.
     */
    @SafeVarargs
    private static void rejectCollectionLink(List<String>... collectionIds) {
        for (List<String> ids : collectionIds) {
            if (ids != null && !ids.isEmpty()) {
                throw new BusinessException(SavedPlaceErrorCode.COLLECTION_LINK_NOT_SUPPORTED);
            }
        }
    }

    /** 장소 공개 id. UUID가 아니면 그런 장소가 없는 것이므로 404입니다. */
    private static List<UUID> placeIds(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        try {
            return ids.stream().filter(Objects::nonNull).map(id -> UUID.fromString(id.strip())).toList();
        } catch (IllegalArgumentException notUuid) {
            throw new BusinessException(SavedPlaceErrorCode.PLACE_NOT_FOUND);
        }
    }

    /** 저장 장소 id. 숫자가 아니면 본인 것이 될 수 없으므로 건너뜁니다 (없는 id와 같음). */
    private static List<Long> savedPlaceIds(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).map(String::strip).filter(id -> id.matches("\\d{1,18}"))
                .map(Long::valueOf).toList();
    }

    private static VisitStatus visitStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return VisitStatus.valueOf(value.strip());
        } catch (IllegalArgumentException unknown) {
            throw new BusinessException(SavedPlaceErrorCode.INVALID_VISIT_STATUS);
        }
    }
}
