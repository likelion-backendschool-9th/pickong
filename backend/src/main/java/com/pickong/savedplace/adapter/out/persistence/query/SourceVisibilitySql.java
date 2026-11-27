package com.pickong.savedplace.adapter.out.persistence.query;

/**
 * 출처 게시물 · 컬렉션을 회원이 볼 수 있는지 판단하는 SQL 조건. 출처를 연결할 때(SavedPlaceTargetLookupAdapter)와
 * 응답에 출처 링크(refId)를 내보낼 때(SavedPlaceQueryAdapter) 같은 기준을 씁니다.
 * <p>
 * 기준은 Visibility.allows와 같습니다: PUBLIC은 모두, FOLLOWERS는 본인과 팔로워(follows), PRIVATE는 본인만.
 * savedplace는 feed · collection 모듈에 의존할 수 없어(6.2) 해당 규칙을 조회 SQL로 옮겨 둡니다.
 * 규칙이 바뀌면 이 파일도 함께 고칩니다.
 */
final class SourceVisibilitySql {

    private SourceVisibilitySql() {
    }

    /**
     * {@code alias}의 visibility · user_id를 {@code viewer}(SQL 식) 기준으로 판단합니다.
     *
     * @param alias  feeds · collections 테이블 별칭
     * @param viewer 보는 회원 id의 SQL 식 (예: {@code :userId}, {@code s.user_id})
     */
    static String readableBy(String alias, String viewer) {
        return """
                (%1$s.user_id = %2$s
                 OR %1$s.visibility = 'PUBLIC'
                 OR (%1$s.visibility = 'FOLLOWERS'
                     AND EXISTS (SELECT 1 FROM follows fw WHERE fw.follower_id = %2$s AND fw.followee_id = %1$s.user_id)))"""
                .formatted(alias, viewer);
    }
}
