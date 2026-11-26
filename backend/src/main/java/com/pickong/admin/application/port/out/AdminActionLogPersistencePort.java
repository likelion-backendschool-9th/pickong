package com.pickong.admin.application.port.out;

import com.pickong.admin.application.dto.AdminActionLogEntry;

/** 작업 로그 저장. 업무 UseCase의 트랜잭션이 필요하며 기존 이력의 수정·삭제는 제공하지 않습니다. */
public interface AdminActionLogPersistencePort {
    long append(AdminActionLogEntry entry);
}
