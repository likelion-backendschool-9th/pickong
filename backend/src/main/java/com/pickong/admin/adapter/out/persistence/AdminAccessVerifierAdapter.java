package com.pickong.admin.adapter.out.persistence;

import com.pickong.admin.adapter.out.persistence.repository.AdminJpaRepository;
import com.pickong.admin.domain.vo.AdminRole;
import com.pickong.admin.domain.vo.AdminStatus;
import com.pickong.common.security.AdminAccessVerifier;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminAccessVerifierAdapter implements AdminAccessVerifier {
    private final AdminJpaRepository admins;
    public AdminAccessVerifierAdapter(AdminJpaRepository admins) { this.admins = admins; }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(long adminId) {
        return adminId > 0 && admins.existsByIdAndStatusAndRoleIn(adminId, AdminStatus.ACTIVE.name(),
                Arrays.stream(AdminRole.values()).map(Enum::name).toList());
    }
}
