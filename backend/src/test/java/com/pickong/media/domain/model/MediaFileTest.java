package com.pickong.media.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.error.BusinessException;
import com.pickong.media.domain.MediaErrorCode;
import com.pickong.media.domain.vo.MediaStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MediaFileTest {

    private static final long USER_ID = 7L;

    @ParameterizedTest
    @CsvSource({"image/jpeg, jpg", "image/png, png", "image/webp, webp", "IMAGE/JPEG, jpg", "' image/png ', png"})
    @DisplayName("허용 종류는 PENDING으로 만들고 feeds/랜덤UUID.확장자 이름을 붙임 (대소문자 앞뒤 공백 무시)")
    void issuesAllowedTypes(String contentType, String extension) {
        MediaFile media = MediaFile.issue(USER_ID, contentType, 1024);

        assertThat(media.getStatus()).isEqualTo(MediaStatus.PENDING);
        assertThat(media.getUserId()).isEqualTo(USER_ID);
        assertThat(media.getMimeType()).isEqualTo(contentType.trim().toLowerCase());
        assertThat(media.getSizeBytes()).isEqualTo(1024);
        assertThat(media.getStorageKey()).matches("feeds/[0-9a-f-]{36}\\." + extension);
    }

    @Test
    @DisplayName("파일 이름은 매번 달라서 추측할 수 없고 회원 id가 들어가지 않음")
    void keyIsRandomAndHasNoUserId() {
        String first = MediaFile.issue(USER_ID, "image/jpeg", 1024).getStorageKey();
        String second = MediaFile.issue(USER_ID, "image/jpeg", 1024).getStorageKey();

        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain("/" + USER_ID + "/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/gif", "image/heic", "application/pdf", "text/html", ""})
    @DisplayName("jpg, png, webp가 아니면 400 MEDIA_TYPE_NOT_ALLOWED")
    void rejectsOtherTypes(String contentType) {
        assertThatThrownBy(() -> MediaFile.issue(USER_ID, contentType, 1024))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_TYPE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("종류가 null이면 400 MEDIA_TYPE_NOT_ALLOWED")
    void rejectsNullType() {
        assertThatThrownBy(() -> MediaFile.issue(USER_ID, null, 1024))
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_TYPE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("크기 경계: 10MB는 허용, 10MB + 1바이트와 0바이트는 400 MEDIA_TOO_LARGE")
    void sizeBoundary() {
        assertThat(MediaFile.issue(USER_ID, "image/jpeg", MediaFile.MAX_SIZE_BYTES).getSizeBytes())
                .isEqualTo(10L * 1024 * 1024);
        assertThatThrownBy(() -> MediaFile.issue(USER_ID, "image/jpeg", MediaFile.MAX_SIZE_BYTES + 1))
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_TOO_LARGE);
        assertThatThrownBy(() -> MediaFile.issue(USER_ID, "image/jpeg", 0))
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_TOO_LARGE);
    }

    @Test
    @DisplayName("남의 사진이면 404 MEDIA_NOT_FOUND (있다는 사실을 숨김)")
    void ownership() {
        MediaFile media = MediaFile.issue(USER_ID, "image/jpeg", 1024);

        media.ensureOwnedBy(USER_ID);
        assertThatThrownBy(() -> media.ensureOwnedBy(99L))
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_NOT_FOUND);
    }

    @Test
    @DisplayName("R2의 실제 파일과 비교: 크기와 종류가 모두 같아야 함 (종류는 대소문자 무시)")
    void matches() {
        MediaFile media = MediaFile.issue(USER_ID, "image/jpeg", 1024);

        assertThat(media.matches(1024, "image/jpeg")).isTrue();
        assertThat(media.matches(1024, "IMAGE/JPEG")).isTrue();
        assertThat(media.matches(1025, "image/jpeg")).isFalse();
        assertThat(media.matches(1024, "image/png")).isFalse();
        assertThat(media.matches(1024, null)).isFalse();
    }

    @Test
    @DisplayName("상태 변화: markUploaded는 주소를 채워 확인됨, markFailed는 실패")
    void stateChanges() {
        MediaFile uploaded = MediaFile.issue(USER_ID, "image/jpeg", 1024);
        uploaded.markUploaded("https://pub.example/feeds/a.jpg");
        MediaFile failed = MediaFile.issue(USER_ID, "image/jpeg", 1024);
        failed.markFailed();

        assertThat(uploaded.isConfirmed()).isTrue();
        assertThat(uploaded.getUrl()).isEqualTo("https://pub.example/feeds/a.jpg");
        assertThat(failed.isFailed()).isTrue();
        assertThat(failed.isConfirmed()).isFalse();
    }
}
