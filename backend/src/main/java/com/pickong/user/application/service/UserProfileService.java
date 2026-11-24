package com.pickong.user.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.user.application.dto.MyProfile;
import com.pickong.user.application.dto.UpdateProfileCommand;
import com.pickong.user.application.port.in.UserCommandUseCase;
import com.pickong.user.application.port.in.UserQueryUseCase;
import com.pickong.user.application.port.out.UserPersistencePort;
import com.pickong.user.domain.UserErrorCode;
import com.pickong.user.domain.model.User;
import com.pickong.user.domain.vo.Username;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보 조회 · 프로필 수정, 닉네임(= 아이디) 사용 가능 여부.
 * 글자 수 · 형식 · 14일 제한 같은 규칙은 domain(User · Username)에 있고, 이 서비스는 저장소가 필요한 확인(사용 중 · 예약어)과 순서만 맡습니다.
 */
@Service
@Transactional(readOnly = true)
public class UserProfileService implements UserQueryUseCase, UserCommandUseCase {

    private final UserPersistencePort userPersistencePort;
    private final Clock clock;

    public UserProfileService(UserPersistencePort userPersistencePort, Clock clock) {
        this.userPersistencePort = userPersistencePort;
        this.clock = clock;
    }

    @Override
    public MyProfile getMe(Long userId) {
        return toMyProfile(load(userId), OffsetDateTime.now(clock));
    }

    /** 형식이 틀리면 400, 예약어이거나 누가 쓰고 있으면 false */
    @Override
    public boolean isUsernameAvailable(String rawUsername) {
        Username username = Username.of(rawUsername);
        return !username.isReserved() && !userPersistencePort.existsByUsername(username.value());
    }

    /**
     * 보낸 항목만 순서대로 바꿉니다. 하나라도 규칙에 어긋나면 아무것도 저장하지 않습니다(전부 확인한 뒤 한 번에 저장).
     * <ol>
     *   <li>아이디: 형식 → 지금과 같으면 그대로 → 14일 제한(domain) → 예약어 · 사용 중</li>
     *   <li>이름 · 소개 · 활동 지역: null을 보내면 지움, 글자 수 제한(domain)</li>
     * </ol>
     * 동시에 같은 아이디로 바꾸는 경우는 DB UNIQUE가 막고 저장소가 USERNAME_TAKEN으로 바꿉니다.
     */
    @Override
    @Transactional
    public MyProfile updateProfile(Long userId, UpdateProfileCommand command) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        User user = load(userId);

        if (command.username() != null) {
            user = changeUsername(user, command.username(), now);
        }
        if (command.name() != null) {
            user = user.rename(command.name().orElse(null), now);
        }
        if (command.bio() != null) {
            user = user.changeBio(command.bio().orElse(null), now);
        }
        if (command.region() != null) {
            user = user.changeRegion(command.region().orElse(null), now);
        }

        userPersistencePort.updateProfile(user);
        return toMyProfile(user, now);
    }

    /** 아이디 변경. 형식 확인(Username.of)과 14일 제한(User)은 domain이, 사용 중 확인은 여기서 합니다. */
    private User changeUsername(User user, Optional<String> requested, OffsetDateTime now) {
        Username username = Username.of(requested.orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_USERNAME)));
        User changed = user.changeUsername(username, now);
        if (changed == user) {
            return user;
        }
        if (username.isReserved() || userPersistencePort.existsByUsername(username.value())) {
            throw new BusinessException(UserErrorCode.USERNAME_TAKEN);
        }
        return changed;
    }

    private User load(Long userId) {
        return userPersistencePort.findById(userId)
                .orElseThrow(() -> new IllegalStateException("로그인한 회원의 행이 없습니다: " + userId));
    }

    /** 다음 아이디 변경 가능 시각은 이미 지났으면 null로 보냅니다. (화면은 null이면 '지금 바꿀 수 있음') */
    private static MyProfile toMyProfile(User user, OffsetDateTime now) {
        OffsetDateTime changeableAt = user.usernameChangeableAt();
        return new MyProfile(user.getPublicId(), user.getUsername(), user.getDisplayName(), user.getProfileImageUrl(),
                user.getBio(), user.getRegion(), user.getFeedCount(), user.getFollowerCount(), user.getFollowingCount(),
                changeableAt != null && now.isBefore(changeableAt) ? changeableAt : null);
    }
}
