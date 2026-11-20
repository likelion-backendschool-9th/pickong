package com.pickong.common.domain;

/**
 * 공개 범위. 게시물 · 컬렉션 · 장소 · 설정이 같은 3단계 규칙을 씁니다.
 */
public enum Visibility {
    PUBLIC,
    FOLLOWERS,
    PRIVATE;

    public boolean allows(ViewerRelation relation) {
        return switch (this) {
            case PUBLIC -> true;
            case FOLLOWERS -> relation == ViewerRelation.OWNER || relation == ViewerRelation.FOLLOWER;
            case PRIVATE -> relation == ViewerRelation.OWNER;
        };
    }
}
