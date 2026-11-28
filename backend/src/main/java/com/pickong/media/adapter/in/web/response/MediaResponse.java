package com.pickong.media.adapter.in.web.response;

import com.pickong.media.application.dto.UploadedMedia;

/** 업로드 확인을 통과한 사진. url은 서명 없이 열리는 공개 URL */
public record MediaResponse(Long mediaId, String url, String contentType, long sizeBytes) {

    public static MediaResponse from(UploadedMedia media) {
        return new MediaResponse(media.mediaId(), media.url(), media.contentType(), media.sizeBytes());
    }
}
