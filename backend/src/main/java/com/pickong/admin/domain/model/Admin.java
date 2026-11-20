package com.pickong.admin.domain.model;

import com.pickong.admin.domain.vo.AdminRole;
import com.pickong.admin.domain.vo.AdminStatus;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 관리자 계정 애그리거트. 역할(SUPER_ADMIN · OPERATOR · CS)과 상태(ACTIVE · DISABLED).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Admin {
    private Long id;
    private String username;
    private String passwordHash;
    private AdminRole role;
    private AdminStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
