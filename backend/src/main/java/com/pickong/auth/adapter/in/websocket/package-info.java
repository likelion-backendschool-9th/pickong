/**
 * WebSocket 연결 인증. 핸드셰이크 때 httpOnly 쿠키의 Access Token을 검증해 Principal을 만들고,
 * /user/queue/** 외 목적지 구독을 거부합니다.
 */
package com.pickong.auth.adapter.in.websocket;
