package com.pickong.user.application.port.in;

import java.time.OffsetDateTime;

/** moderation 전용 계정 상태 기능. 모든 메서드는 호출자의 쓰기 트랜잭션에 참여합니다. */
public interface UserStatusUseCase {
    void lock(long userId);

    /** 회원 행을 잠그고 탈퇴·익명화 계정의 신규 제재를 거절합니다. 경고에도 적용합니다. */
    void lockForSanction(long userId);

    void suspend(long userId, OffsetDateTime until, OffsetDateTime now);

    void ban(long userId, OffsetDateTime now);

    void lift(long userId, boolean hasRemainingRestriction, OffsetDateTime now);

    /** 자동 만료는 만료일이 지난 SUSPENDED만 복구하며 BANNED·탈퇴 상태는 복구하지 않습니다. */
    void expireSuspension(long userId, boolean hasRemainingRestriction, OffsetDateTime now);
}
