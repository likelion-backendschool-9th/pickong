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

/** 탈퇴 규칙: 신청 · 취소(30일 안) · 익명화(30일 뒤). 순수 domain 규칙이라 DB 없이 확인합니다. */
class UserWithdrawalRulesTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T00:00:00Z");

    private static User active() {
        return User.register(Username.of("seoyeon"), "서연", "kong@kakao.com", "https://img/1.png", NOW.minusDays(100))
                .toBuilder().id(42L).bio("소개").region("서울").build();
    }

    @Test
    @DisplayName("탈퇴 신청: ACTIVE → WITHDRAWN_PENDING, 신청 시각 기록, 익명화 시각은 신청 + 30일")
    void request() {
        User pending = active().requestWithdrawal(NOW);

        assertThat(pending.getStatus()).isEqualTo(UserStatus.WITHDRAWN_PENDING);
        assertThat(pending.getWithdrawalRequestedAt()).isEqualTo(NOW);
        assertThat(pending.anonymizeAt()).isEqualTo(NOW.plusDays(30));
        assertThat(pending.canLogin()).as("탈퇴 중에는 로그인 · 재발급 불가").isFalse();
    }

    @Test
    @DisplayName("탈퇴 신청: ACTIVE가 아니면(이미 탈퇴 중 · 정지) WITHDRAWAL_NOT_ALLOWED")
    void requestOnlyActive() {
        User pending = active().requestWithdrawal(NOW);
        User suspended = active().toBuilder().status(UserStatus.SUSPENDED).build();

        for (User user : new User[] {pending, suspended}) {
            assertThatThrownBy(() -> user.requestWithdrawal(NOW))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.WITHDRAWAL_NOT_ALLOWED);
        }
    }

    @Test
    @DisplayName("탈퇴 취소: 30일 안이면 ACTIVE로 돌아오고 신청 시각을 지운다")
    void cancelWithinGrace() {
        User pending = active().requestWithdrawal(NOW);
        OffsetDateTime justBefore = NOW.plusDays(30).minusSeconds(1);

        assertThat(pending.canCancelWithdrawal(justBefore)).isTrue();
        User restored = pending.cancelWithdrawal(justBefore);
        assertThat(restored.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(restored.getWithdrawalRequestedAt()).isNull();
        assertThat(restored.canLogin()).isTrue();
    }

    @Test
    @DisplayName("탈퇴 취소: 30일이 지나면(익명화 전이라도) WITHDRAWAL_NOT_CANCELLABLE, 탈퇴 중이 아니어도 같음")
    void cancelAfterGraceRefused() {
        User pending = active().requestWithdrawal(NOW);
        OffsetDateTime due = NOW.plusDays(30);

        assertThat(pending.canCancelWithdrawal(due)).isFalse();
        for (User user : new User[] {pending, active()}) {
            assertThatThrownBy(() -> user.cancelWithdrawal(due))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.WITHDRAWAL_NOT_CANCELLABLE);
        }
    }

    @Test
    @DisplayName("익명화 시점: 신청 후 정확히 30일부터. 그 전 · 탈퇴 중이 아닌 회원은 아님")
    void dueForAnonymization() {
        User pending = active().requestWithdrawal(NOW);

        assertThat(pending.isDueForAnonymization(NOW.plusDays(30).minusSeconds(1))).isFalse();
        assertThat(pending.isDueForAnonymization(NOW.plusDays(30))).isTrue();
        assertThat(active().isDueForAnonymization(NOW.plusDays(365))).isFalse();
    }

    @Test
    @DisplayName("익명화: 개인정보를 비우고 아이디를 deleted_{id}로 바꾸고 WITHDRAWN · 익명화 시각 기록 (카운터 등은 그대로)")
    void anonymize() {
        OffsetDateTime due = NOW.plusDays(31);
        User anonymized = active().toBuilder().followerCount(3).build().requestWithdrawal(NOW).anonymize(due);

        assertThat(anonymized.getUsername()).isEqualTo("deleted_42");
        assertThat(anonymized.getEmail()).isNull();
        assertThat(anonymized.getDisplayName()).isNull();
        assertThat(anonymized.getBio()).isNull();
        assertThat(anonymized.getRegion()).isNull();
        assertThat(anonymized.getProfileImageUrl()).isNull();
        assertThat(anonymized.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(anonymized.getDeletedAt()).isEqualTo(due);
        assertThat(anonymized.getFollowerCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("익명화: 아직 30일이 안 된 회원이면 거절 (예약 작업이 잘못 부르는 것을 막음)")
    void anonymizeTooEarly() {
        User pending = active().requestWithdrawal(NOW);

        assertThatThrownBy(() -> pending.anonymize(NOW.plusDays(29))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("deleted_로 시작하는 아이디는 예약어라 회원이 쓸 수 없다 (익명화 아이디와 겹치지 않게)")
    void anonymizedPrefixReserved() {
        assertThat(Username.of("deleted_42").isReserved()).isTrue();
        assertThat(Username.of("deleted_kong").isReserved()).isTrue();
        assertThat(Username.of("deletedkong").isReserved()).isFalse();
    }
}
