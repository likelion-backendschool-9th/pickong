/**
 * 알림 모듈: 앱 안 알림 생성 · 조회 · 읽음. 다른 모듈의 이벤트를 받기만 하는 최상위 모듈이며, domain 모델 없이 처리합니다 (4.2 2단계).
 *
 * <p>소유 테이블: notifications
 * <p>의존 모듈: user, social, post, collection, creator, conversation, plan (이벤트 구독)
 * <p>설계: backend_blueprint.md 4.4
 */
package com.pickong.notification;
