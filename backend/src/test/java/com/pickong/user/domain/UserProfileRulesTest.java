package com.pickong.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.error.BusinessException;
import com.pickong.user.domain.model.User;
import com.pickong.user.domain.vo.Username;
import java.time.OffsetDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 프로필 수정 규칙 단위 테스트: 아이디 14일 제한, 이름 · 소개 · 활동 지역 정리와 글자 수. */
class UserProfileRulesTest {

    private static final OffsetDateTime SIGNED_UP = OffsetDateTime.parse("2026-11-01T00:00:00Z");

    private static User newUser() {
        return User.register(Username.of("seoyeon"), "서연", null, null, SIGNED_UP);
    }

    private static void assertError(ThrowingCallable call, UserErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(code);
    }

    @Test
    @DisplayName("아이디: 가입 후 첫 변경은 바로 되고, 변경 시각이 기록된다")
    void firstUsernameChange() {
        OffsetDateTime now = SIGNED_UP.plusMinutes(5);

        User changed = newUser().changeUsername(Username.of("kong"), now);

        assertThat(changed.getUsername()).isEqualTo("kong");
        assertThat(changed.getUsernameChangedAt()).isEqualTo(now);
        assertThat(changed.usernameChangeableAt()).isEqualTo(now.plusDays(14));
    }

    @Test
    @DisplayName("아이디: 바꾼 뒤 14일 안에는 USERNAME_CHANGE_TOO_SOON, 14일이 지나면 다시 바꿀 수 있다")
    void usernameCooldown() {
        OffsetDateTime changedAt = SIGNED_UP.plusDays(1);
        User changed = newUser().changeUsername(Username.of("kong"), changedAt);

        assertError(() -> changed.changeUsername(Username.of("kong2"), changedAt.plusDays(14).minusSeconds(1)),
                UserErrorCode.USERNAME_CHANGE_TOO_SOON);
        assertThat(changed.changeUsername(Username.of("kong2"), changedAt.plusDays(14)).getUsername()).isEqualTo("kong2");
    }

    @Test
    @DisplayName("아이디: 지금과 같은 값이면 바꾸지 않은 것으로 보고 14일 제한도 시작하지 않는다")
    void sameUsernameIsNoChange() {
        User changed = newUser().changeUsername(Username.of("kong"), SIGNED_UP.plusDays(1));

        User same = changed.changeUsername(Username.of("KONG"), SIGNED_UP.plusDays(2));

        assertThat(same).isSameAs(changed);
    }

    @Test
    @DisplayName("이름: null · 빈 값이면 지우고, 앞뒤 공백은 정리하고, 21자는 INVALID_NAME")
    void rename() {
        User user = newUser();

        assertThat(user.rename(null, SIGNED_UP).getDisplayName()).isNull();
        assertThat(user.rename("   ", SIGNED_UP).getDisplayName()).isNull();
        assertThat(user.rename("  콩이  ", SIGNED_UP).getDisplayName()).isEqualTo("콩이");
        assertError(() -> user.rename("가".repeat(21), SIGNED_UP), UserErrorCode.INVALID_NAME);
    }

    @Test
    @DisplayName("소개: 150자까지(이모지 한 글자 = 1), 줄바꿈은 유지, 비면 null, 151자는 INVALID_BIO")
    void bio() {
        User user = newUser();

        assertThat(user.changeBio("😀".repeat(150), SIGNED_UP).getBio()).hasSize(300);
        assertThat(user.changeBio(" 카페 투어\n주말 산책 ", SIGNED_UP).getBio()).isEqualTo("카페 투어\n주말 산책");
        assertThat(user.changeBio("", SIGNED_UP).getBio()).isNull();
        assertError(() -> user.changeBio("a".repeat(151), SIGNED_UP), UserErrorCode.INVALID_BIO);
    }

    @Test
    @DisplayName("활동 지역: 20자까지, 비면 null, 21자는 INVALID_REGION")
    void region() {
        User user = newUser();

        assertThat(user.changeRegion(" 부산 해운대 ", SIGNED_UP).getRegion()).isEqualTo("부산 해운대");
        assertThat(user.changeRegion(null, SIGNED_UP).getRegion()).isNull();
        assertError(() -> user.changeRegion("가".repeat(21), SIGNED_UP), UserErrorCode.INVALID_REGION);
    }
}
