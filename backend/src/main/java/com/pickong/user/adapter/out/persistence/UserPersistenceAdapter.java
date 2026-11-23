package com.pickong.user.adapter.out.persistence;

import com.pickong.common.error.BusinessException;
import com.pickong.user.adapter.out.persistence.entity.UserJpaEntity;
import com.pickong.user.adapter.out.persistence.repository.UserJpaRepository;
import com.pickong.user.application.port.out.UserPersistencePort;
import com.pickong.user.domain.UserErrorCode;
import com.pickong.user.domain.model.User;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** 회원 저장소. JPA 엔티티는 이 어댑터 밖으로 내보내지 않고 domain 모델로 바꿔서 돌려줍니다. */
@Component
public class UserPersistenceAdapter implements UserPersistencePort {

    /** V1 마이그레이션의 UNIQUE 인덱스 이름. 위반되면 어느 값이 겹쳤는지 이 이름으로 구분합니다. */
    private static final String USERNAME_UNIQUE = "uk_users_username_lower";
    private static final String EMAIL_UNIQUE = "uk_users_email_lower";

    private final UserJpaRepository userJpaRepository;

    public UserPersistenceAdapter(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public Optional<User> findById(Long userId) {
        return userJpaRepository.findById(userId).map(UserJpaEntity::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userJpaRepository.existsByUsernameIgnoreCase(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmailIgnoreCase(email);
    }

    /**
     * saveAndFlush로 INSERT를 바로 실행해, UNIQUE 위반이 커밋 때가 아니라 여기서 나게 합니다.
     * 그래야 어떤 값이 겹쳤는지 알맞은 오류 코드로 바꿔 사용자에게 알려 줄 수 있습니다.
     */
    @Override
    public User save(User user) {
        try {
            return userJpaRepository.saveAndFlush(UserJpaEntity.from(user)).toDomain();
        } catch (DataIntegrityViolationException e) {
            String message = String.valueOf(e.getMostSpecificCause().getMessage());
            if (message.contains(USERNAME_UNIQUE)) {
                throw new BusinessException(UserErrorCode.USERNAME_TAKEN);
            }
            if (message.contains(EMAIL_UNIQUE)) {
                throw new BusinessException(UserErrorCode.EMAIL_ALREADY_REGISTERED);
            }
            throw e;
        }
    }
}
