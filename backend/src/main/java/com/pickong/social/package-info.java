/**
 * 관계 모듈: 팔로우 · 차단, 관계 판정(팔로워인지, 차단했는지). 규칙이 단순해서 domain 모델 없이 포트 메서드로 처리합니다 (4.2 2단계).
 *
 * <p>소유 테이블: follows, user_blocks
 * <p>의존 모듈: user
 * <p>설계: backend_blueprint.md 4.4
 */
package com.pickong.social;
