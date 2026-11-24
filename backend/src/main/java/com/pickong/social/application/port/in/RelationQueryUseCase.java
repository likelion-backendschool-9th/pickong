package com.pickong.social.application.port.in;

import java.util.List;

/** 팔로우·차단 조회 유스케이스. */
public interface RelationQueryUseCase {

    /** userId가 팔로우하는 사람들의 내부 id 목록. 피드 모듈이 팔로잉 피드 조회에 사용합니다. */
    List<Long> getFollowingIds(Long userId);
}
