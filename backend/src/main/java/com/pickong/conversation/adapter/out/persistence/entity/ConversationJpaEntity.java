package com.pickong.conversation.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * conversations 테이블 JPA 엔티티.
 * 1:1 또는 그룹 대화방 정보를 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "conversations")
public class ConversationsJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대화방 종류. 값 목록: DIRECT, GROUP */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** 그룹 대화방 제목. DIRECT는 NULL. */
    @Column(name = "title")
    private String title;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
