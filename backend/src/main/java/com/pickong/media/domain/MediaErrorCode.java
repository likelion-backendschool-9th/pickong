package com.pickong.media.domain;

import com.pickong.common.error.ErrorCode;
import lombok.Getter;

@Getter
public enum MediaErrorCode implements ErrorCode {

    MEDIA_TYPE_NOT_ALLOWED(400, "jpg, png, webp 사진만 올릴 수 있어요."),
    MEDIA_TOO_LARGE(400, "사진은 10MB까지 올릴 수 있어요."),
    // 남의 사진 id도 같은 응답을 줘서 있다는 사실을 숨김
    MEDIA_NOT_FOUND(404, "사진을 찾을 수 없어요."),
    MEDIA_NOT_UPLOADED(400, "사진 업로드가 끝나지 않았어요. 다시 올려 주세요."),
    MEDIA_FILE_MISMATCH(400, "올린 파일이 요청한 형식이나 크기와 달라요. 다시 올려 주세요."),
    MEDIA_UPLOAD_FAILED(409, "실패한 업로드예요. 사진을 새로 올려 주세요.");

    private final int status;
    private final String message;

    MediaErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
