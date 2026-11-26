package com.pickong.admin.adapter.out.persistence.repository;

import com.pickong.admin.adapter.out.persistence.entity.AdminActionLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActionLogJpaRepository extends JpaRepository<AdminActionLogJpaEntity, Long> {
}
