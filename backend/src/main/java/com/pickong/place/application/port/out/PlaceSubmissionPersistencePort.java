package com.pickong.place.application.port.out;

import com.pickong.place.domain.model.PlaceSubmission;
import java.util.List;
import java.util.Optional;

/**
 * 장소 제보 저장소. 구현은 place 모듈 어댑터만 가집니다.
 * 승인 · 반려로 바뀐 값은 {@link #updateIfPending}으로만 반영합니다.
 */
public interface PlaceSubmissionPersistencePort {

    /** 심사 대기 제보를 새로 저장하고, DB가 부여한 id가 채워진 값을 돌려줍니다. */
    PlaceSubmission save(PlaceSubmission submission);

    Optional<PlaceSubmission> findById(long id);

    /** 관리자 목록. 최신 접수 순입니다. */
    List<PlaceSubmission> findAll();

    /**
     * 지금 대기인 행만 승인 · 반려 결과로 바꿉니다.
     *
     * @return 한 행이 바뀌었으면 true. 없거나 이미 처리됐으면 false
     */
    boolean updateIfPending(PlaceSubmission submission);
}
