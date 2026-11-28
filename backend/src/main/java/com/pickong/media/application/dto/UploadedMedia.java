package com.pickong.media.application.dto;

import com.pickong.media.domain.model.MediaFile;

/**
 * 업로드 확인을 통과한 사진
 *
 * @param url 브라우저에서 바로 열 수 있는 공개 주소
 */
public record UploadedMedia(Long mediaId, String url, String contentType, long sizeBytes) {

    public static UploadedMedia from(MediaFile media) {
        return new UploadedMedia(media.getId(), media.getUrl(), media.getMimeType(), media.getSizeBytes());
    }
}
