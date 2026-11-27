package com.pickong.social.application.port.in;

import com.pickong.common.domain.ViewerRelation;
import java.util.List;

/** 팔로우·차단 조회 유스케이스. */
public interface RelationQueryUseCase {

    /** userId가 팔로우하는 사람들의 내부 id 목록. 피드 모듈이 팔로잉 피드 조회에 사용합니다. */
    List<Long> getFollowingIds(Long userId);

    /**
     * 콘텐츠를 보는 사람과 소유자의 관계. 공개 범위 판정(Visibility.allows)의 입력으로 씀
     * 1. viewerId가 null이면 ANONYMOUS → 2. 본인이면 OWNER → 3. 소유자를 팔로우하면 FOLLOWER → 4. 그 외 OTHER
     * 요청마다 follows를 새로 읽어서 팔로우를 끊으면 바로 반영됨
     */
    ViewerRelation relationOf(Long viewerId, Long ownerId);
}
