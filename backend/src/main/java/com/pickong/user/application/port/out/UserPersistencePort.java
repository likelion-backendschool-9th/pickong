package com.pickong.user.application.port.out;

import com.pickong.user.domain.model.User;
import java.util.Optional;

/** 회원(users) 저장소 포트. 구현: {@code user.adapter.out.persistence.UserPersistenceAdapter} */
public interface UserPersistencePort {

    Optional<User> findById(Long userId);
}
