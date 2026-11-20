/**
 * DM 모듈: 1:1 · 그룹 대화방, 참여자, 메시지, 읽음, 실시간 전달. HTTP와 STOMP가 같은 MessageUseCase를 씁니다.
 *
 * <p>소유 테이블: conversations, conversation_members, messages
 * <p>의존 모듈: user, social, media, place, post
 * <p>설계: backend_blueprint.md 5.4
 */
package com.pickong.conversation;
