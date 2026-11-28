package com.pickong.conversation.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * messages 테이블 JPA 엔티티.
 * 대화방 메시지를 저장한다. 첨부 컬럼은 보내기(STOMP) 이슈에서 형식을 정하고 추가한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "messages")
public class MessageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /** 프론트가 메시지마다 만드는 UUID. UNIQUE(sender_id, client_message_id) */
    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
