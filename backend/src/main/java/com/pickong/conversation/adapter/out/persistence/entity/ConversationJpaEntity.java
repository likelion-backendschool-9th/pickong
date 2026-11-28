package com.pickong.conversation.adapter.out.persistence.entity;

import com.pickong.conversation.domain.model.Conversation;
import com.pickong.conversation.domain.model.ConversationMember;
import com.pickong.conversation.domain.vo.ConversationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * conversations 테이블 JPA 엔티티.
 * 1:1 또는 그룹 대화방 정보를 저장한다. 1:1 대화방 만들기는 동시성 때문에 저장소의 INSERT ... ON CONFLICT로 한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "conversations")
public class ConversationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대화방 종류. 값 목록: DIRECT, GROUP */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** 그룹 대화방 제목. DIRECT는 NULL. */
    @Column(name = "title", length = 50)
    private String title;

    /** 1:1 대화방 중복 방지 키 "작은 id:큰 id". GROUP은 NULL. */
    @Column(name = "direct_key", length = 41)
    private String directKey;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Conversation toDomain(List<ConversationMember> members) {
        return Conversation.builder()
                .id(id)
                .type(ConversationType.valueOf(type))
                .title(title)
                .directKey(directKey)
                .createdBy(createdBy)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .members(members)
                .build();
    }
}
