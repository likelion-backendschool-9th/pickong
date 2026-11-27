/**
 * WebSocket · STOMP 공통 설정과 실시간 전달 (blueprint backend 5.4, common 5.9).
 * <ul>
 *   <li>엔드포인트 "/ws" — 서블릿 경로(/api/v1) 아래라 실제 주소는 /api/v1/ws</li>
 *   <li>보내기 /app/**, 받기 /user/queue/** (simple broker)</li>
 *   <li>모듈은 {@link com.pickong.common.websocket.UserPushRelay}로만 보냅니다 (SimpMessagingTemplate 직접 사용 금지)</li>
 * </ul>
 * 인증은 auth 모듈이 Spring 타입(HandshakeInterceptor · ChannelInterceptor) 빈으로 등록하고, 여기서는 모아 붙이기만 합니다.
 */
package com.pickong.common.websocket;
