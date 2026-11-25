package com.pickong.user.application.port.out;

import com.pickong.user.domain.model.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 회원(users) 저장소 포트. 구현: {@code user.adapter.out.persistence.UserPersistenceAdapter} */
public interface UserPersistencePort {

    Optional<User> findById(Long userId);

    /** username(대소문자 무시)으로 회원을 조회합니다. */
    Optional<User> findByUsername(String username);

    /** 이 아이디(소문자)을 쓰는 회원이 있는지 (lower(username) 인덱스) */
    boolean existsByUsername(String username);

    /** 이 이메일(대소문자 무시)을 가입 이메일로 쓰는 회원이 있는지 (lower(email) 인덱스) */
    boolean existsByEmail(String email);

    /**
     * 새 회원을 저장합니다. 미리 확인했어도 동시에 같은 아이디 · 이메일로 가입하면 DB UNIQUE가 막고,
     * 그 경우 USERNAME_TAKEN · EMAIL_ALREADY_REGISTERED로 바꿔 던집니다.
     */
    User save(User user);

    /**
     * 프로필 항목(아이디 · 이름 · 소개 · 활동 지역 · 아이디 변경 시각)만 저장합니다. 다른 컬럼은 건드리지 않습니다.
     * 동시에 같은 아이디로 바꾸면 DB UNIQUE가 막고 USERNAME_TAKEN으로 바꿔 던집니다.
     */
    void updateProfile(User user);

    /** 탈퇴 상태(상태 · 탈퇴 신청 시각)만 저장합니다. 집계 컬럼 등 다른 컬럼은 건드리지 않습니다. */
    void updateWithdrawal(User user);

    /** 익명화한 값(아이디 · 이메일 · 이름 · 소개 · 지역 · 사진 · 상태 · 익명화 시각)만 저장합니다. */
    void updateAnonymized(User user);

    /** 탈퇴 중(WITHDRAWN_PENDING)이고 탈퇴 신청 시각이 threshold 이전인 회원 id (신청 시각 순) */
    List<Long> findWithdrawalIdsRequestedBefore(OffsetDateTime threshold);
}
