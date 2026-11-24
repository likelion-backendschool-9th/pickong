package com.pickong.social.application.port.out;

import java.util.List;

/** 팔로우 저장소 포트. */
public interface RelationRepository {

    void saveFollow(Long followerId, Long followeeId);

    /** 팔로우 관계를 삭제합니다. 존재하지 않으면 false를 반환합니다. */
    boolean deleteFollow(Long followerId, Long followeeId);

    boolean existsFollow(Long followerId, Long followeeId);

    List<Long> findFollowingIds(Long followerId);
}
