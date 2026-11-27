package com.pickong.media.adapter.in.web;

import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.media.adapter.in.web.request.IssueUploadRequest;
import com.pickong.media.adapter.in.web.response.MediaResponse;
import com.pickong.media.adapter.in.web.response.UploadTicketResponse;
import com.pickong.media.application.port.in.MediaUploadUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사진 업로드. 로그인 필수 (SecurityConfig의 나머지 경로 규칙)
 * 흐름: POST /media/presigned → 브라우저가 R2에 직접 PUT → POST /media/{mediaId}/confirm
 */
@Validated
@RestController
@RequestMapping("/media")
public class MediaController {

    private final MediaUploadUseCase mediaUploadUseCase;

    public MediaController(MediaUploadUseCase mediaUploadUseCase) {
        this.mediaUploadUseCase = mediaUploadUseCase;
    }

    // Presigned URL 발급 — 201 Created. 사진 기록(PENDING)이 새로 생기기 때문
    @PostMapping("/presigned")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadTicketResponse issueUploadUrl(@CurrentUser LoginUser user,
            @Valid @RequestBody IssueUploadRequest request) {
        return UploadTicketResponse.from(
                mediaUploadUseCase.issueUploadUrl(user.userId(), request.contentType(), request.sizeBytes()));
    }

    // 업로드 확인 — 200. 같은 요청을 다시 보내도 같은 결과
    @PostMapping("/{mediaId}/confirm")
    public MediaResponse confirmUpload(@CurrentUser LoginUser user, @PathVariable @Positive Long mediaId) {
        return MediaResponse.from(mediaUploadUseCase.confirmUpload(user.userId(), mediaId));
    }
}
