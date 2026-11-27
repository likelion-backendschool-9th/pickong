/**
 * WebSocket 연결 인증 · 권한 (blueprint 5.4.3).
 * <ul>
 *   <li>핸드셰이크: 보안 필터 체인이 쿠키를 검증한 뒤, 회원 id와 토큰 만료 시각을 세션 속성에 넣음</li>
 *   <li>프레임: CONNECT는 로그인 필수, 구독은 /user/queue/**만, 보내기는 /app/**만, 만료 · 이용할 수 없는 계정은 연결 종료</li>
 * </ul>
 */
package com.pickong.auth.adapter.in.websocket;
