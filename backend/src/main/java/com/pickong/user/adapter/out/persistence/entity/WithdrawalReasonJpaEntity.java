package com.pickong.user.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** withdrawal_reasons 테이블 (V11). 회원 id · 시각 없이 사유 · 내용 · 날짜만 둡니다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "withdrawal_reasons")
public class WithdrawalReasonJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reason", nullable = false, length = 30)
    private String reason;

    @Column(name = "detail")
    private String detail;

    @Column(name = "requested_on", nullable = false)
    private LocalDate requestedOn;

    public static WithdrawalReasonJpaEntity of(String reason, String detail, LocalDate requestedOn) {
        WithdrawalReasonJpaEntity entity = new WithdrawalReasonJpaEntity();
        entity.reason = reason;
        entity.detail = detail;
        entity.requestedOn = requestedOn;
        return entity;
    }
}
