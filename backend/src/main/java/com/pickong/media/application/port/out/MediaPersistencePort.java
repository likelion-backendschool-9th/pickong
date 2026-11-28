package com.pickong.media.application.port.out;

import com.pickong.media.domain.model.MediaFile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** media_files 저장과 조회 out-port */
public interface MediaPersistencePort {

    /** 새 기록이면 id를 채워서, 있던 기록이면 바뀐 값을 저장해서 돌려줌 */
    MediaFile save(MediaFile media);

    /** 확인(confirm) 중에 같은 사진의 확인 요청이 동시에 와도 한 번씩만 처리되게 행을 잠그고 읽음 */
    Optional<MediaFile> findByIdForUpdate(Long mediaId);

    /** 게시물에 연결할 사진들을 잠그고 읽음. 같은 사진으로 동시에 두 게시물을 만들면 뒤 요청이 앞 요청이 끝날 때까지 기다림 */
    List<MediaFile> findAllByIdsForUpdate(Collection<Long> mediaIds);
}
