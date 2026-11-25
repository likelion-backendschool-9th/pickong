package com.pickong.user.domain.event;

import java.time.OffsetDateTime;

/** 상태 저장 후 발행합니다. 소비 모듈에 회원 모델이나 JPA 엔티티를 전달하지 않습니다. */
public record UserStatusChangedEvent(long userId, String status, OffsetDateTime occurredAt) {
}
