package com.pickong.user.adapter.out.persistence;

import com.pickong.user.adapter.out.persistence.entity.UserJpaEntity;
import com.pickong.user.adapter.out.persistence.repository.UserJpaRepository;
import com.pickong.user.application.port.out.UserPersistencePort;
import com.pickong.user.domain.model.User;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 회원 저장소. JPA 엔티티는 이 어댑터 밖으로 내보내지 않고 domain 모델로 바꿔서 돌려줍니다. */
@Component
public class UserPersistenceAdapter implements UserPersistencePort {

    private final UserJpaRepository userJpaRepository;

    public UserPersistenceAdapter(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public Optional<User> findById(Long userId) {
        return userJpaRepository.findById(userId).map(UserJpaEntity::toDomain);
    }
}
