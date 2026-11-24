package com.pickong.user.application.port.in;

/**
 * 회원 집계 컬럼 증감 유스케이스.
 * 팔로우·게시물 등 다른 모듈이 같은 트랜잭션 안에서 호출합니다.
 * UPDATE로 해당 컬럼만 바꾸므로 다른 모듈이 동시에 바꾸는 값을 덮어쓰지 않습니다.
 */
public interface UserCounterUseCase {

    void incrementFollowerCount(Long userId);

    void decrementFollowerCount(Long userId);

    void incrementFollowingCount(Long userId);

    void decrementFollowingCount(Long userId);
}
