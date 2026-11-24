package com.pickong.user.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.user.application.dto.WithdrawalStatus;
import com.pickong.user.application.port.in.UserWithdrawalUseCase;
import com.pickong.user.application.port.out.UserPersistencePort;
import com.pickong.user.application.port.out.WithdrawalReasonPersistencePort;
import com.pickong.user.domain.UserErrorCode;
import com.pickong.user.domain.event.UserWithdrawnEvent;
import com.pickong.user.domain.model.User;
import com.pickong.user.domain.vo.WithdrawalReason;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserWithdrawalService implements UserWithdrawalUseCase {

    /** '기타' 내용 최대 길이. 프론트 탈퇴 화면 입력 제한(maxLength 300)과 같음 */
    static final int DETAIL_MAX = 300;

    /** 탈퇴 사유의 날짜는 한국 날짜로 남깁니다 */
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final UserPersistencePort users;
    private final WithdrawalReasonPersistencePort reasons;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserWithdrawalService(UserPersistencePort users, WithdrawalReasonPersistencePort reasons,
                                 ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.reasons = reasons;
        this.events = events;
        this.clock = clock;
    }

    /** 순서: 사유 확인(틀리면 400, 아무것도 저장 안 함) → 회원 상태 바꿈(ACTIVE만) → 사유를 회원 id 없이 날짜만 붙여 저장. 한 트랜잭션입니다. */
    @Override
    @Transactional
    public void requestWithdrawal(Long userId, String reason, String detail) {
        WithdrawalReason parsed = WithdrawalReason.parse(reason)
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_WITHDRAWAL_REASON));
        String normalizedDetail = normalizeDetail(parsed, detail);

        OffsetDateTime now = OffsetDateTime.now(clock);
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        users.updateWithdrawal(user.requestWithdrawal(now));
        reasons.save(parsed, normalizedDetail, LocalDate.now(clock.withZone(KOREA)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WithdrawalStatus> findCancellableWithdrawal(Long userId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return users.findById(userId)
                .filter(user -> user.canCancelWithdrawal(now))
                .map(user -> new WithdrawalStatus(user.getUsername(), user.getWithdrawalRequestedAt(), user.anonymizeAt()));
    }

    @Override
    @Transactional
    public String cancelWithdrawal(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        User cancelled = user.cancelWithdrawal(OffsetDateTime.now(clock));
        users.updateWithdrawal(cancelled);
        return cancelled.getUsername();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findWithdrawalsDueForAnonymization() {
        return users.findWithdrawalIdsRequestedBefore(OffsetDateTime.now(clock).minus(User.WITHDRAWAL_GRACE_PERIOD));
    }

    /**
     * 다시 읽어서 지금도 익명화할 때인지 확인합니다(목록을 읽은 뒤 취소됐을 수 있음).
     * 다른 모듈의 정리(UserWithdrawnEvent 구독)를 먼저 같은 트랜잭션에서 하고, 그다음 회원 행을 익명화합니다.
     */
    @Override
    @Transactional
    public boolean anonymize(Long userId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<User> user = users.findById(userId).filter(found -> found.isDueForAnonymization(now));
        if (user.isEmpty()) {
            return false;
        }
        events.publishEvent(new UserWithdrawnEvent(userId));
        users.updateAnonymized(user.get().anonymize(now));
        return true;
    }

    /** 기타 내용: 앞뒤 공백 정리 → 비면 null → 기타인데 비었거나 300자(코드 포인트) 초과면 400 */
    private static String normalizeDetail(WithdrawalReason reason, String detail) {
        String trimmed = detail == null || detail.isBlank() ? null : detail.strip();
        if (reason == WithdrawalReason.ETC && trimmed == null) {
            throw new BusinessException(UserErrorCode.INVALID_WITHDRAWAL_REASON);
        }
        if (trimmed != null && trimmed.codePointCount(0, trimmed.length()) > DETAIL_MAX) {
            throw new BusinessException(UserErrorCode.INVALID_WITHDRAWAL_REASON);
        }
        return trimmed;
    }
}
