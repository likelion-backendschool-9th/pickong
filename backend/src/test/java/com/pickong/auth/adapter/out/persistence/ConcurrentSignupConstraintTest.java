package com.pickong.auth.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.application.port.out.SocialAccountPersistencePort;
import com.pickong.auth.domain.AuthErrorCode;
import com.pickong.auth.domain.vo.SocialProvider;
import com.pickong.common.error.BusinessException;
import com.pickong.user.application.port.out.UserPersistencePort;
import com.pickong.user.domain.UserErrorCode;
import com.pickong.user.domain.model.User;
import com.pickong.user.domain.vo.Username;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동시 가입(두 요청이 서비스의 사전 확인을 동시에 통과한 경우)을 DB UNIQUE가 막고,
 * 저장소 어댑터가 그 위반을 알맞은 오류 코드로 바꾸는지 확인합니다.
 * 서비스의 사전 확인을 거치지 않고 어댑터에 바로 같은 값을 두 번 저장해서 그 상황을 만듭니다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ConcurrentSignupConstraintTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-23T00:00:00Z");

    @Autowired
    UserPersistencePort users;

    @Autowired
    SocialAccountPersistencePort socialAccounts;

    private static User newUser(String username, String email) {
        return User.register(Username.of(username), null, email, null, NOW);
    }

    @Test
    @DisplayName("같은 닉네임(대소문자만 다름)이 DB까지 가면 USERNAME_TAKEN으로 바뀐다")
    void usernameUniqueMapped() {
        users.save(newUser("seoyeon", null));

        assertThatThrownBy(() -> users.save(User.register(new Username("SeoYeon"), null, null, null, NOW)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USERNAME_TAKEN);
    }

    @Test
    @DisplayName("같은 가입 이메일이 DB까지 가면 EMAIL_ALREADY_REGISTERED로 바뀐다")
    void emailUniqueMapped() {
        users.save(newUser("seoyeon", "kong@kakao.com"));

        assertThatThrownBy(() -> users.save(newUser("jimin", "KONG@kakao.com")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("같은 소셜 계정을 두 회원에 연결하려 하면 SOCIAL_ACCOUNT_ALREADY_LINKED로 바뀐다")
    void socialAccountUniqueMapped() {
        Long seoyeon = users.save(newUser("seoyeon", null)).getId();
        Long jimin = users.save(newUser("jimin", null)).getId();
        socialAccounts.link(seoyeon, SocialProvider.KAKAO, "4242", null);

        assertThatThrownBy(() -> socialAccounts.link(jimin, SocialProvider.KAKAO, "4242", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
    }
}
