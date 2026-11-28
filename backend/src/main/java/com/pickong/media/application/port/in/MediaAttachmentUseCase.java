package com.pickong.media.application.port.in;

import java.util.Collection;

/** 다른 모듈(feed)이 사진을 게시물에 연결하기 전에 쓸 수 있는 사진인지 확인함 */
public interface MediaAttachmentUseCase {

    /**
     * 사진들이 모두 이 회원의 것이고 업로드 확인이 끝났는지 확인함. 확인한 사진 행은 호출한 트랜잭션이 끝날 때까지 잠김
     * 호출하는 쪽의 트랜잭션 안에서 불러야 됨 (잠금을 유지한 채 게시물에 연결해야 동시 요청을 막을 수 있음)
     *
     * @throws com.pickong.common.error.BusinessException 404 MEDIA_NOT_FOUND (없거나 남의 사진),
     *         400 MEDIA_NOT_UPLOADED (업로드 확인 전), 409 MEDIA_UPLOAD_FAILED (실패한 사진)
     */
    void ensureAttachable(Long userId, Collection<Long> mediaIds);
}
