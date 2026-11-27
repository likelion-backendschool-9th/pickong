package com.pickong.media.application.port.out;

import com.pickong.media.domain.model.MediaFile;
import java.util.Optional;

/** media_files 저장과 조회 out-port */
public interface MediaPersistencePort {

    /** 새 기록이면 id를 채워서, 있던 기록이면 바뀐 값을 저장해서 돌려줌 */
    MediaFile save(MediaFile media);

    /** 확인(confirm) 중에 같은 사진의 확인 요청이 동시에 와도 한 번씩만 처리되게 행을 잠그고 읽음 */
    Optional<MediaFile> findByIdForUpdate(Long mediaId);
}
