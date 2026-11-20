package com.pickong.common.domain;

/**
 * 조회하는 사람과 콘텐츠 소유자의 관계. {@link Visibility#allows(ViewerRelation)}의 입력이며, 값은 social 모듈이 계산합니다.
 */
public enum ViewerRelation {
    OWNER,
    FOLLOWER,
    OTHER,
    ANONYMOUS
}
