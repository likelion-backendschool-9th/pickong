package com.pickong.feed.application.port.in;

/**
 * 다른 모듈이 게시물 열람 가능 여부만 확인할 때 쓰는 in-port (예: 신고 접수)
 * 판단은 상세 조회와 같은 Feed.ensureReadable 한 곳에서 해서, 공개 범위 규칙이 바뀌어도 다른 모듈이 따로 고칠 필요가 없음
 */
public interface FeedAccessQueryUseCase {

    /**
     * viewerId가 feedId 게시물을 볼 수 있으면 true
     * 없는 게시물, 삭제됨, 남의 숨김 글, 공개 범위 밖(팔로우하지 않은 사람의 FOLLOWERS, 남의 PRIVATE)이면 false
     * viewerId가 null이면 비로그인으로 판단함
     */
    boolean isReadableBy(Long feedId, Long viewerId);
}
