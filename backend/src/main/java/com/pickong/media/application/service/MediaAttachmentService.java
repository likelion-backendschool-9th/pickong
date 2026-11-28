package com.pickong.media.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.media.application.port.in.MediaAttachmentUseCase;
import com.pickong.media.application.port.out.MediaPersistencePort;
import com.pickong.media.domain.MediaErrorCode;
import com.pickong.media.domain.model.MediaFile;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaAttachmentService implements MediaAttachmentUseCase {

    private final MediaPersistencePort mediaPersistencePort;

    public MediaAttachmentService(MediaPersistencePort mediaPersistencePort) {
        this.mediaPersistencePort = mediaPersistencePort;
    }

    // 잠금을 게시물 저장까지 유지해야 돼서 부르는 쪽 트랜잭션이 없으면 바로 오류를 냄
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void ensureAttachable(Long userId, Collection<Long> mediaIds) {
        if (mediaIds.isEmpty()) {
            return;
        }
        // 1. 잠그고 읽기 → 2. 하나라도 없으면 404 → 3. 사진마다 소유자(404) → 상태(실패 409, 확인 전 400)
        List<MediaFile> found = mediaPersistencePort.findAllByIdsForUpdate(mediaIds);
        if (found.size() != mediaIds.stream().distinct().count()) {
            throw new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND);
        }
        for (MediaFile media : found) {
            // 소유자 확인을 상태 확인보다 먼저 해야 남의 사진의 업로드 상태가 드러나지 않음
            media.ensureOwnedBy(userId);
            if (media.isFailed()) {
                throw new BusinessException(MediaErrorCode.MEDIA_UPLOAD_FAILED);
            }
            if (!media.isConfirmed()) {
                throw new BusinessException(MediaErrorCode.MEDIA_NOT_UPLOADED);
            }
        }
    }
}
