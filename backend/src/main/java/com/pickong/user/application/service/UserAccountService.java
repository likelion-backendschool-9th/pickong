package com.pickong.user.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.user.application.dto.RegisterUserCommand;
import com.pickong.user.application.dto.RegisteredUser;
import com.pickong.user.application.port.in.UserAccountUseCase;
import com.pickong.user.application.port.out.UserPersistencePort;
import com.pickong.user.domain.UserErrorCode;
import com.pickong.user.domain.model.User;
import com.pickong.user.domain.vo.Username;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.context.ApplicationEventPublisher;
import com.pickong.user.domain.event.UserSuspensionExpiryCheckEvent;

/**
 * auth 모듈 전용 회원 계정 기능: 로그인 가능 여부, 신규 회원 생성.
 * (아이디 사용 가능 여부 · 내 정보 · 프로필 수정은 {@link UserProfileService})
 */
@Service
@Transactional(readOnly = true)
public class UserAccountService implements UserAccountUseCase {

    private final UserPersistencePort userPersistencePort;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public UserAccountService(UserPersistencePort userPersistencePort, Clock clock, ApplicationEventPublisher events) {
        this.userPersistencePort = userPersistencePort;
        this.clock = clock;
        this.events = events;
    }

    /** 회원을 찾아 로그인 가능한 상태인지 domain 규칙(User.canLogin)으로 판단합니다. 회원이 없으면 false. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    public boolean canLogin(Long userId) {
        if (userId == null || userId <= 0) return false;
        return userPersistencePort.findById(userId).map(user -> {
            if (user.hasExpiredSuspension(OffsetDateTime.now(clock))) {
                events.publishEvent(new UserSuspensionExpiryCheckEvent(userId));
                return userPersistencePort.findById(userId).map(User::canLogin).orElse(false);
            }
            return user.canLogin();
        }).orElse(false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = false)
    public boolean canLoginForUpdate(Long userId) {
        if (userId == null || userId <= 0) return false;
        return userPersistencePort.findByIdForUpdate(userId).map(user -> {
            if (user.hasExpiredSuspension(OffsetDateTime.now(clock))) {
                events.publishEvent(new UserSuspensionExpiryCheckEvent(userId));
                return userPersistencePort.findByIdForUpdate(userId).map(User::canLogin).orElse(false);
            }
            return user.canLogin();
        }).orElse(false);
    }

    /**
     * 순서: 아이디 형식 → 예약어 · 사용 중 → 이메일 사용 중 → 이름 규칙(User.register) → 저장.
     * 미리 확인하는 이유는 사용자에게 정확한 사유를 알려 주기 위해서이고,
     * 동시에 같은 값으로 가입하는 경우는 DB UNIQUE가 최종으로 막습니다(저장소 어댑터에서 같은 오류 코드로 변환).
     */
    @Override
    @Transactional
    public RegisteredUser register(RegisterUserCommand command) {
        Username username = Username.of(command.username());
        if (username.isReserved() || userPersistencePort.existsByUsername(username.value())) {
            throw new BusinessException(UserErrorCode.USERNAME_TAKEN);
        }
        if (command.email() != null && userPersistencePort.existsByEmail(command.email())) {
            throw new BusinessException(UserErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        User user = User.register(username, command.name(), command.email(), command.profileImageUrl(), OffsetDateTime.now(clock));
        User saved = userPersistencePort.save(user);
        return new RegisteredUser(saved.getId(), saved.getUsername());
    }
}
