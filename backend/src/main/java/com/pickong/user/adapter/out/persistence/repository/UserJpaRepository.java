package com.pickong.user.adapter.out.persistence.repository;

import com.pickong.user.adapter.out.persistence.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    /** lower(username) UNIQUE 인덱스와 같은 기준으로 비교합니다. */
    @Query("select count(u) > 0 from UserJpaEntity u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    /** lower(email) UNIQUE 인덱스와 같은 기준으로 비교합니다. */
    @Query("select count(u) > 0 from UserJpaEntity u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);
}
