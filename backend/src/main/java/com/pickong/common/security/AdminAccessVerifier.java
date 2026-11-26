package com.pickong.common.security;

/** 보안 필터용 SPI. 계정 소유 모듈이 활성 상태를 검증하며 common은 관리자 테이블을 모릅니다. */
public interface AdminAccessVerifier {
    boolean isActive(long adminId);
}
