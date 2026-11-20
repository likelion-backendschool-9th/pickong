package com.pickong.creator.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * creator_payout_accounts 테이블 JPA 엔티티.
 * 크리에이터 정산 계좌 정보를 저장한다.
 * creator_id는 UNIQUE (1:1 관계).
 * account_number_encrypted, resident_number_encrypted는 AES-GCM 암호화 값.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "creator_payout_accounts")
public class CreatorPayoutAccountsJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", nullable = false, unique = true)
    private Long creatorId;

    @Column(name = "bank_code", nullable = false)
    private String bankCode;

    /** AES-GCM 암호화된 계좌번호. EncryptionPort를 통해 처리. */
    @Column(name = "account_number_encrypted", nullable = false)
    private String accountNumberEncrypted;

    @Column(name = "account_holder", nullable = false)
    private String accountHolder;

    /**
     * AES-GCM 암호화된 주민등록번호.
     * ⚠ 별도 정리 스케줄러(ResidentNumberPurgeScheduler)로 삭제 예정.
     */
    @Column(name = "resident_number_encrypted")
    private String residentNumberEncrypted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
