package com.pickong.admin.application.service;

import com.pickong.admin.application.dto.AdminActionLogEntry;
import com.pickong.admin.application.port.in.AdminActionLogUseCase;
import com.pickong.admin.application.port.out.AdminActionLogPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AdminActionLogService implements AdminActionLogUseCase {
    private final AdminActionLogPersistencePort logs;

    public AdminActionLogService(AdminActionLogPersistencePort logs) {
        this.logs = logs;
    }

    @Override
    public long record(AdminActionLogEntry entry) {
        if (entry == null || entry.adminId() <= 0 || entry.createdAt() == null
                || entry.actionType() == null || entry.actionType().isBlank()
                || (entry.targetType() == null) != (entry.targetId() == null)
                || (entry.targetType() != null && (entry.targetType().isBlank() || entry.targetId() <= 0))) {
            throw new IllegalArgumentException("관리자와 처리 시각, 액션, 올바른 대상 정보가 필요합니다.");
        }
        return logs.append(entry);
    }
}
