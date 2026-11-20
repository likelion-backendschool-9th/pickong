package com.pickong.creator.domain.model;

import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 정산 계좌 애그리거트. 크리에이터당 1개입니다.
 * <p>계좌번호 · 주민등록번호는 AES-GCM으로 암호화된 값만 가집니다 (복호화는 EncryptionPort).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PayoutAccount {
    private Long id;
    private Long creatorId;
    private String bankCode;
    private String accountNumberEncrypted;
    private String accountHolder;
    private String residentNumberEncrypted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
