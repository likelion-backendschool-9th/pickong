package com.pickong.place.application.port.in;

import com.pickong.place.application.dto.PlaceSubmissionView;
import com.pickong.place.application.dto.SubmitPlaceCommand;
import java.util.List;

/** 장소 제보 접수와 관리자 심사. 관리자 id는 호출하는 쪽이 인증된 주체에서 꺼냅니다. */
public interface PlaceSubmissionUseCase {

    /** 대기 제보만 만듭니다. 공개 장소는 만들지 않습니다. */
    PlaceSubmissionView submit(SubmitPlaceCommand command);

    /** 필터 없이 전체. 페이지 객체(page, size, totalCount)는 쓰지 않습니다. */
    List<PlaceSubmissionView> list();

    PlaceSubmissionView get(long submissionId);

    PlaceSubmissionView approve(long submissionId, long reviewerAdminId);

    PlaceSubmissionView reject(long submissionId, long reviewerAdminId, String reason);
}
