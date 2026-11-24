package com.pickong.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.error.BusinessException;
import com.pickong.user.domain.model.User;
import com.pickong.user.domain.vo.UserStatus;
import com.pickong.user.domain.vo.Username;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 닉네임(= 사용자 ID) · 이름 · 신규 회원 규칙 단위 테스트. 프론트 nickname.test.ts · name.test.ts와 같은 기준입니다. */
class UserRulesTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-23T00:00:00Z");

    private static void assertInvalidUsername(String raw) {
        assertThatThrownBy(() -> Username.of(raw))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_USERNAME);
    }

    @Test
    @DisplayName("닉네임: 영문으로 시작하는 3~20자, 영문 소문자 · 숫자 · _ · .만")
    void usernameFormat() {
        assertThat(Username.of("seoyeon").value()).isEqualTo("seoyeon");
        assertThat(Username.of("seo_yeon.92").value()).isEqualTo("seo_yeon.92");
        assertThat(Username.of("a".repeat(20)).value()).hasSize(20);
        assertInvalidUsername("ab");
        assertInvalidUsername("a".repeat(21));
        assertInvalidUsername("서연");
        assertInvalidUsername("seo-yeon");
        assertInvalidUsername("92seoyeon");
        assertInvalidUsername("_seoyeon");
        assertInvalidUsername(null);
    }

    @Test
    @DisplayName("닉네임: 마침표는 끝 · 연속으로 쓸 수 없다")
    void usernameDots() {
        assertInvalidUsername("seoyeon.");
        assertInvalidUsername("seo..yeon");
    }

    @Test
    @DisplayName("닉네임: 앞뒤 공백은 지우고 대문자는 소문자로 맞춘다")
    void usernameNormalized() {
        assertThat(Username.of("  SeoYeon ").value()).isEqualTo("seoyeon");
    }

    @Test
    @DisplayName("닉네임: admin · pickong 같은 예약어를 구분한다")
    void reserved() {
        assertThat(Username.of("admin").isReserved()).isTrue();
        assertThat(Username.of("Pickong").isReserved()).isTrue();
        assertThat(Username.of("seoyeon").isReserved()).isFalse();
    }

    @Test
    @DisplayName("신규 회원: ACTIVE, 카운터 0, 공개 ID · 생성 시각이 채워진다")
    void register() {
        User user = User.register(Username.of("seoyeon"), "서연", "a@gmail.com", "https://lh3.googleusercontent.com/a", NOW);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPublicId()).isNotNull();
        assertThat(user.getUsername()).isEqualTo("seoyeon");
        assertThat(user.getDisplayName()).isEqualTo("서연");
        assertThat(user.getFollowerCount()).isZero();
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        assertThat(user.canLogin()).isTrue();
    }

    @Test
    @DisplayName("이름: 비우면 null, 앞뒤 공백 정리, 한글 · 이모지 포함 20자까지")
    void name() {
        assertThat(User.register(Username.of("seoyeon"), "   ", null, null, NOW).getDisplayName()).isNull();
        assertThat(User.register(Username.of("seoyeon"), null, null, null, NOW).getDisplayName()).isNull();
        assertThat(User.register(Username.of("seoyeon"), " 서연 ", null, null, NOW).getDisplayName()).isEqualTo("서연");
        assertThat(User.register(Username.of("seoyeon"), "가".repeat(19) + "🌱", null, null, NOW).getDisplayName()).hasSize(21);

        assertThatThrownBy(() -> User.register(Username.of("seoyeon"), "가".repeat(21), null, null, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_NAME);
    }
}
