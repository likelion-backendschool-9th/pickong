package com.pickong.admin.application.port.in;

import com.pickong.admin.application.dto.AdminActionLogEntry;

/** 업무 모듈이 사용하는 관리자 작업 기록 공개 포트. 호출자 트랜잭션에 참여합니다. */
public interface AdminActionLogUseCase {
    long record(AdminActionLogEntry entry);
}
