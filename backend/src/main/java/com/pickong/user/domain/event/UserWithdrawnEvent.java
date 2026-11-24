package com.pickong.user.domain.event;

/**
 * 탈퇴 유예 기간(30일)이 지나 회원을 익명화하는 중입니다. 익명화와 <b>같은 트랜잭션</b>에서 동기로 발행합니다.
 * <p>
 * 구독하는 모듈은 이 회원의 개인 데이터를 지웁니다(auth: 소셜 연결 · 토큰, feed: 게시물 등).
 * 한 곳이라도 실패하면 익명화까지 되돌아가 회원이 WITHDRAWN_PENDING으로 남고, 다음 날 예약 작업이 다시 처리합니다.
 * 그래서 구독하는 쪽은 여러 번 불려도 결과가 같게(이미 지운 것은 건너뛰게) 만들어야 합니다.
 *
 * @param userId 익명화하는 회원 id
 */
public record UserWithdrawnEvent(Long userId) {
}
