package com.pickong.admin.adapter.out.persistence.repository;

import com.pickong.admin.adapter.out.persistence.entity.AdminJpaEntity;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminJpaRepository extends JpaRepository<AdminJpaEntity, Long> {
    boolean existsByIdAndStatusAndRoleIn(long id, String status, Collection<String> roles);
}
