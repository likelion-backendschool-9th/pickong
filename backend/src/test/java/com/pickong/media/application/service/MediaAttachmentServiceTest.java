package com.pickong.media.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.error.BusinessException;
import com.pickong.media.domain.MediaErrorCode;
import com.pickong.media.domain.model.MediaFile;
import com.pickong.media.domain.vo.MediaStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 게시물에 연결할 사진 확인 규칙 (소유자 → 상태 순서) */
class MediaAttachmentServiceTest {

    private static final long ME = 1L;
    private static final long OTHER = 2L;

    private final MediaUploadServiceTest.FakeMediaPersistence persistence = new MediaUploadServiceTest.FakeMediaPersistence();
    private final MediaAttachmentService service = new MediaAttachmentService(persistence);

    private long media(long owner, MediaStatus status) {
        return persistence.save(MediaFile.builder().userId(owner).status(status).build()).getId();
    }

    @Test
    void 내_사진이고_확인이_끝났으면_통과() {
        long uploaded = media(ME, MediaStatus.UPLOADED);
        long processed = media(ME, MediaStatus.PROCESSED);

        assertThatCode(() -> service.ensureAttachable(ME, List.of(uploaded, processed))).doesNotThrowAnyException();
    }

    @Test
    void 빈_목록은_조회없이_통과() {
        assertThatCode(() -> service.ensureAttachable(ME, List.of())).doesNotThrowAnyException();
    }

    @Test
    void 없는_사진이_하나라도_있으면_404() {
        long uploaded = media(ME, MediaStatus.UPLOADED);

        assertThatThrownBy(() -> service.ensureAttachable(ME, List.of(uploaded, 999L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_NOT_FOUND);
    }

    @Test
    void 남의_사진은_상태와_관계없이_404() {
        // 남의 실패한 사진도 409가 아니라 404여야 남의 사진의 상태가 드러나지 않음
        long othersFailed = media(OTHER, MediaStatus.FAILED);

        assertThatThrownBy(() -> service.ensureAttachable(ME, List.of(othersFailed)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_NOT_FOUND);
    }

    @Test
    void 업로드_확인_전이면_400() {
        long pending = media(ME, MediaStatus.PENDING);

        assertThatThrownBy(() -> service.ensureAttachable(ME, List.of(pending)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_NOT_UPLOADED);
    }

    @Test
    void 실패한_사진이면_409() {
        long failed = media(ME, MediaStatus.FAILED);

        assertThatThrownBy(() -> service.ensureAttachable(ME, List.of(failed)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_UPLOAD_FAILED);
    }
}
