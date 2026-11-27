package com.pickong.media.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.media.application.dto.UploadTicket;
import com.pickong.media.application.dto.UploadedMedia;
import com.pickong.media.application.port.in.MediaUploadUseCase;
import com.pickong.media.application.port.out.MediaPersistencePort;
import com.pickong.media.application.port.out.ObjectStoragePort;
import com.pickong.media.application.port.out.ObjectStoragePort.StoredObject;
import com.pickong.media.application.port.out.ObjectStoragePort.UploadUrl;
import com.pickong.media.domain.MediaErrorCode;
import com.pickong.media.domain.model.MediaFile;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaUploadService implements MediaUploadUseCase {

    private static final Logger log = LoggerFactory.getLogger(MediaUploadService.class);

    private final MediaPersistencePort mediaPersistencePort;
    private final ObjectStoragePort objectStoragePort;

    public MediaUploadService(MediaPersistencePort mediaPersistencePort, ObjectStoragePort objectStoragePort) {
        this.mediaPersistencePort = mediaPersistencePort;
        this.objectStoragePort = objectStoragePort;
    }

    /**
     * 1. R2 설정이 없으면 503 (쓸 수 없는 URL과 PENDING 기록을 만들지 않음)
     * → 2. 종류와 크기 검사 후 PENDING 기록 저장 → 3. 그 key로 Presigned URL 서명
     */
    @Override
    @Transactional
    public UploadTicket issueUploadUrl(Long userId, String contentType, long sizeBytes) {
        if (!objectStoragePort.isConfigured()) {
            throw new BusinessException(MediaErrorCode.MEDIA_STORAGE_UNAVAILABLE);
        }
        MediaFile media = mediaPersistencePort.save(MediaFile.issue(userId, contentType, sizeBytes));
        UploadUrl upload = objectStoragePort.createUploadUrl(media.getStorageKey(), media.getMimeType(), sizeBytes);
        return new UploadTicket(media.getId(), upload.url(), upload.headers());
    }

    /**
     * 1. 사진 기록 잠그고 읽기 → 2. 본인 사진인지 → 3. 이미 확인됐으면 그대로, 이미 실패했으면 409
     * → 4. R2에 HEAD로 물어봄 → 5. 없으면 400 (PENDING 유지) → 6. 종류나 크기가 다르면 FAILED + R2 파일 삭제
     * → 7. 같으면 UPLOADED + 공개 URL 저장
     * 6번은 FAILED 저장을 남긴 채 오류를 돌려줘야 해서 BusinessException이 나도 롤백하지 않음
     * (이 메서드에서 저장하고 예외를 던지는 곳은 6번뿐이라 다른 예외는 롤백할 변경이 없음)
     * 3번에서 다시 R2를 확인하지 않아도 되는 이유: Presigned URL에 If-None-Match: * 조건이 서명돼 있어 확인 뒤에 덮어쓸 수 없음
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public UploadedMedia confirmUpload(Long userId, Long mediaId) {
        MediaFile media = mediaPersistencePort.findByIdForUpdate(mediaId)
                .orElseThrow(() -> new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND));
        media.ensureOwnedBy(userId);

        if (media.isConfirmed()) {
            return UploadedMedia.from(media);
        }
        if (media.isFailed()) {
            throw new BusinessException(MediaErrorCode.MEDIA_UPLOAD_FAILED);
        }

        Optional<StoredObject> stored = objectStoragePort.findObject(media.getStorageKey());
        if (stored.isEmpty()) {
            throw new BusinessException(MediaErrorCode.MEDIA_NOT_UPLOADED);
        }
        if (!media.matches(stored.get().contentLength(), stored.get().contentType())) {
            media.markFailed();
            mediaPersistencePort.save(media);
            deleteQuietly(media);
            throw new BusinessException(MediaErrorCode.MEDIA_FILE_MISMATCH);
        }

        media.markUploaded(objectStoragePort.publicUrl(media.getStorageKey()));
        return UploadedMedia.from(mediaPersistencePort.save(media));
    }

    /**
     * 잘못 올라온 파일을 지움. R2 오류(네트워크, 권한)가 나도 예외를 밖으로 내지 않음
     * 런타임 예외가 나가면 FAILED 저장까지 롤백되기 때문. 남은 파일은 FAILED 상태로 남아 정리 스케줄러(#171)가 지움
     */
    private void deleteQuietly(MediaFile media) {
        try {
            objectStoragePort.deleteObject(media.getStorageKey());
        } catch (RuntimeException e) {
            log.warn("FAILED 사진의 R2 파일 삭제 실패, 정리 스케줄러 대상으로 남김 mediaId={} key={}",
                    media.getId(), media.getStorageKey(), e);
        }
    }
}
