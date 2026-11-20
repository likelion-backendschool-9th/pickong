/**
 * 일정 도메인 정책: 도착 · 출발 시각 계산, 일정 충돌 검증(경고만, 막지 않음), 방문 순서 최적화.
 * 입력은 이동 시간 표(route 결과를 바꾼 plan의 VO)이며 외부 API · DB를 모릅니다.
 * Spring · JPA · HTTP에 의존하지 않습니다.
 */
package com.pickong.plan.domain.policy;
