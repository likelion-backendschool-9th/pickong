package com.pickong.moderation.application.port.out;

import com.pickong.moderation.application.dto.ReportPartyView;
import java.util.Collection;
import java.util.Map;

/** 신고자와 작성자 이름. 회원 저장소는 쓰지 않고, 화면에 필요한 값만 읽습니다. */
public interface ReportPartyLookupPort {

    /** 없는 회원은 맵에 없습니다. 빈 목록은 조회하지 않고 빈 맵을 돌려줍니다. */
    Map<Long, ReportPartyView> findByUserIds(Collection<Long> userIds);
}
