package com.pickong.user.application.service;

import com.pickong.user.application.port.in.UserAccountUseCase;
import com.pickong.user.application.port.out.UserPersistencePort;
import com.pickong.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserAccountService implements UserAccountUseCase {

    private final UserPersistencePort userPersistencePort;

    public UserAccountService(UserPersistencePort userPersistencePort) {
        this.userPersistencePort = userPersistencePort;
    }

    /** 회원을 찾아 로그인 가능한 상태인지 domain 규칙(User.canLogin)으로 판단합니다. 회원이 없으면 false. */
    @Override
    public boolean canLogin(Long userId) {
        return userPersistencePort.findById(userId).map(User::canLogin).orElse(false);
    }
}
