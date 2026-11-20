package com.pickong.conversation.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * messages 테이블 JPA 엔티티.
 * 대화방 메시지를 저장한다.
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

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 첨부 파일 종류. 값 목록: IMAGE, PLAN */
    @Column(name = "attachment_type", length = 20)
    private String attachmentType;

    @Column(name = "attachment_media_id")
    private Long attachmentMediaId;

    /** 첨부된 일정 id. attachment_type이 PLAN일 때 세팅. */
    @Column(name = "attachment_plan_id")
    private Long attachmentPlanId;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
