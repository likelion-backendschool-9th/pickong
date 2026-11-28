package com.pickong.media.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.media.application.dto.UploadTicket;
import com.pickong.media.application.dto.UploadedMedia;
import com.pickong.media.application.port.out.MediaPersistencePort;
import com.pickong.media.application.port.out.ObjectStoragePort;
import com.pickong.media.domain.MediaErrorCode;
import com.pickong.media.domain.model.MediaFile;
import com.pickong.media.domain.vo.MediaStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 진짜 DB와 R2 대신 메모리에서 동작하는 가짜 포트로 서비스 흐름만 확인함 (blueprint: 서비스 테스트는 Fake Port) */
class MediaUploadServiceTest {

    private static final long ME = 1L;
    private static final long OTHER = 2L;

    private FakeMediaPersistence persistence;
    private FakeObjectStorage storage;
    private MediaUploadService service;

    @BeforeEach
    void setUp() {
        persistence = new FakeMediaPersistence();
        storage = new FakeObjectStorage();
        service = new MediaUploadService(persistence, storage);
    }

    @Test
    @DisplayName("발급: PENDING 기록을 저장하고 그 파일 이름 종류 크기로 Presigned URL을 만듦")
    void issue() {
        UploadTicket ticket = service.issueUploadUrl(ME, "image/jpeg", 2048);

        MediaFile saved = persistence.get(ticket.mediaId());
        assertThat(saved.getStatus()).isEqualTo(MediaStatus.PENDING);
        assertThat(saved.getUserId()).isEqualTo(ME);
        assertThat(ticket.uploadUrl()).isEqualTo("signed:" + saved.getStorageKey() + ":image/jpeg:2048");
        assertThat(ticket.uploadHeaders()).containsEntry("Content-Type", "image/jpeg").containsEntry("If-None-Match", "*");
    }

    @Test
    @DisplayName("발급: R2 설정이 없으면 503 MEDIA_STORAGE_UNAVAILABLE, PENDING 기록을 만들지 않음")
    void issueWithoutStorageConfig() {
        storage.configured = false;

        assertThatThrownBy(() -> service.issueUploadUrl(ME, "image/jpeg", 2048))
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_STORAGE_UNAVAILABLE);
        assertThat(persistence.count()).isZero();
    }

    @Test
    @DisplayName("확인 성공: R2에 같은 크기와 종류로 있으면 UPLOADED + 공개 주소")
    void confirmSuccess() {
        UploadTicket ticket = service.issueUploadUrl(ME, "image/png", 2048);
        String key = persistence.get(ticket.mediaId()).getStorageKey();
        storage.put(key, 2048, "image/png");

        UploadedMedia media = service.confirmUpload(ME, ticket.mediaId());

        assertThat(media.url()).isEqualTo("https://pub.example/" + key);
        assertThat(persistence.get(ticket.mediaId()).getStatus()).isEqualTo(MediaStatus.UPLOADED);
    }

    @Test
    @DisplayName("확인을 두 번 해도 같은 결과 (R2에 다시 묻지 않음)")
    void confirmIsIdempotent() {
        UploadTicket ticket = service.issueUploadUrl(ME, "image/png", 2048);
        storage.put(persistence.get(ticket.mediaId()).getStorageKey(), 2048, "image/png");
        UploadedMedia first = service.confirmUpload(ME, ticket.mediaId());
        int headCalls = storage.headCalls;

        UploadedMedia second = service.confirmUpload(ME, ticket.mediaId());

        assertThat(second).isEqualTo(first);
        assertThat(storage.headCalls).isEqualTo(headCalls);
    }

    @Test
    @DisplayName("남의 사진 id로 확인하면 404, 상태는 그대로")
    void confirmOthersMedia() {
        UploadTicket ticket = service.issueUploadUrl(ME, "image/png", 2048);
        storage.put(persistence.get(ticket.mediaId()).getStorageKey(), 2048, "image/png");

        assertThatThrownBy(() -> service.confirmUpload(OTHER, ticket.mediaId()))
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_NOT_FOUND);
        assertThat(persistence.get(ticket.mediaId()).getStatus()).isEqualTo(MediaStatus.PENDING);
    }

    @Test
    @DisplayName("없는 사진 id는 404")
    void confirmUnknownMedia() {
        assertThatThrownBy(() -> service.confirmUpload(ME, 999L))
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_NOT_FOUND);
    }

    @Test
    @DisplayName("R2에 파일이 아직 없으면 400 MEDIA_NOT_UPLOADED, PENDING 유지 (다시 올리고 확인 가능)")
    void confirmBeforeUpload() {
        UploadTicket ticket = service.issueUploadUrl(ME, "image/png", 2048);

        assertThatThrownBy(() -> service.confirmUpload(ME, ticket.mediaId()))
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_NOT_UPLOADED);
        assertThat(persistence.get(ticket.mediaId()).getStatus()).isEqualTo(MediaStatus.PENDING);
    }

    @Test
    @DisplayName("크기가 다르면 400 MEDIA_FILE_MISMATCH, FAILED 저장 + R2 파일 삭제")
    void confirmSizeMismatch() {
        UploadTicket ticket = service.issueUploadUrl(ME, "image/png", 2048);
        String key = persistence.get(ticket.mediaId()).getStorageKey();
        storage.put(key, 9999, "image/png");

        assertThatThrownBy(() -> service.confirmUpload(ME, ticket.mediaId()))
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_FILE_MISMATCH);
        assertThat(persistence.get(ticket.mediaId()).getStatus()).isEqualTo(MediaStatus.FAILED);
        assertThat(storage.deletedKeys).containsExactly(key);
    }

    @Test
    @DisplayName("종류가 다르면 400 MEDIA_FILE_MISMATCH, FAILED 저장 + R2 파일 삭제")
    void confirmTypeMismatch() {
        UploadTicket ticket = service.issueUploadUrl(ME, "image/png", 2048);
        String key = persistence.get(ticket.mediaId()).getStorageKey();
        storage.put(key, 2048, "text/html");

        assertThatThrownBy(() -> service.confirmUpload(ME, ticket.mediaId()))
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_FILE_MISMATCH);
        assertThat(persistence.get(ticket.mediaId()).getStatus()).isEqualTo(MediaStatus.FAILED);
        assertThat(storage.deletedKeys).containsExactly(key);
    }

    @Test
    @DisplayName("R2 파일 삭제가 실패해도 FAILED는 그대로 저장되고 MEDIA_FILE_MISMATCH만 나감 (R2 오류가 밖으로 나가면 롤백됨)")
    void confirmMismatchWhenDeleteFails() {
        UploadTicket ticket = service.issueUploadUrl(ME, "image/png", 2048);
        storage.put(persistence.get(ticket.mediaId()).getStorageKey(), 9999, "image/png");
        storage.failDelete = true;

        assertThatThrownBy(() -> service.confirmUpload(ME, ticket.mediaId()))
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_FILE_MISMATCH);
        assertThat(persistence.get(ticket.mediaId()).getStatus()).isEqualTo(MediaStatus.FAILED);
    }

    @Test
    @DisplayName("이미 실패한 사진을 다시 확인하면 409 MEDIA_UPLOAD_FAILED")
    void confirmFailedMedia() {
        UploadTicket ticket = service.issueUploadUrl(ME, "image/png", 2048);
        storage.put(persistence.get(ticket.mediaId()).getStorageKey(), 1, "image/png");
        assertThatThrownBy(() -> service.confirmUpload(ME, ticket.mediaId()));

        assertThatThrownBy(() -> service.confirmUpload(ME, ticket.mediaId()))
                .extracting("errorCode").isEqualTo(MediaErrorCode.MEDIA_UPLOAD_FAILED);
    }

    /** media_files 대신 메모리 Map. 저장할 때 id를 1부터 붙임 */
    // 사진 연결 확인 테스트(MediaAttachmentServiceTest)도 같이 씀
    static class FakeMediaPersistence implements MediaPersistencePort {
        private final Map<Long, MediaFile> rows = new HashMap<>();
        private long nextId = 1;

        @Override
        public MediaFile save(MediaFile media) {
            Long id = media.getId() != null ? media.getId() : nextId++;
            MediaFile saved = MediaFile.builder()
                    .id(id).userId(media.getUserId()).status(media.getStatus()).storageKey(media.getStorageKey())
                    .url(media.getUrl()).mimeType(media.getMimeType()).sizeBytes(media.getSizeBytes())
                    .retryCount(media.getRetryCount())
                    .build();
            rows.put(id, saved);
            return copy(saved);
        }

        @Override
        public Optional<MediaFile> findByIdForUpdate(Long mediaId) {
            return Optional.ofNullable(rows.get(mediaId)).map(this::copy);
        }

        @Override
        public java.util.List<MediaFile> findAllByIdsForUpdate(java.util.Collection<Long> mediaIds) {
            return mediaIds.stream().distinct().sorted().map(rows::get).filter(java.util.Objects::nonNull).map(this::copy).toList();
        }

        MediaFile get(Long id) {
            return rows.get(id);
        }

        int count() {
            return rows.size();
        }

        // 서비스가 받은 객체를 바꿔도 저장된 값이 바뀌지 않게 복사본을 줌 (DB와 같은 동작)
        private MediaFile copy(MediaFile m) {
            return MediaFile.builder()
                    .id(m.getId()).userId(m.getUserId()).status(m.getStatus()).storageKey(m.getStorageKey())
                    .url(m.getUrl()).mimeType(m.getMimeType()).sizeBytes(m.getSizeBytes()).retryCount(m.getRetryCount())
                    .build();
        }
    }

    /** R2 대신 메모리 Map. Presigned URL은 넘겨받은 값을 이어 붙인 문자열로 대신함 */
    static class FakeObjectStorage implements ObjectStoragePort {
        private final Map<String, StoredObject> objects = new HashMap<>();
        final List<String> deletedKeys = new ArrayList<>();
        int headCalls;
        boolean configured = true;
        boolean failDelete;

        void put(String key, long size, String type) {
            objects.put(key, new StoredObject(size, type));
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public UploadUrl createUploadUrl(String key, String contentType, long contentLength) {
            return new UploadUrl("signed:" + key + ":" + contentType + ":" + contentLength,
                    Map.of("Content-Type", contentType, "If-None-Match", "*"));
        }

        @Override
        public Optional<StoredObject> findObject(String key) {
            headCalls++;
            return Optional.ofNullable(objects.get(key));
        }

        @Override
        public void deleteObject(String key) {
            if (failDelete) {
                throw new IllegalStateException("R2 연결 실패 흉내");
            }
            objects.remove(key);
            deletedKeys.add(key);
        }

        @Override
        public String publicUrl(String key) {
            return "https://pub.example/" + key;
        }
    }
}
