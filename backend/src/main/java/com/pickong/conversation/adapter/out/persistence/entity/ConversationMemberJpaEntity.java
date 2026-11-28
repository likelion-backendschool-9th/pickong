package com.pickong.conversation.adapter.out.persistence.entity;

import com.pickong.conversation.domain.model.ConversationMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * conversation_members 테이블 JPA 엔티티.
 * 대화방 참여자 정보를 저장한다. 나가기 · 다시 참여 · 읽음은 조건부 UPDATE(저장소)로 바꾼다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "conversation_members")
public class ConversationMemberJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    /** 알림 끄기 (값만 저장) */
    @Column(name = "muted", nullable = false)
    private boolean muted;

    /** 그룹에 처음 들어오는 참여자 행 */
    public static ConversationMemberJpaEntity joining(long conversationId, long userId, OffsetDateTime joinedAt,
                                                      Long lastReadMessageId) {
        ConversationMemberJpaEntity entity = new ConversationMemberJpaEntity();
        entity.conversationId = conversationId;
        entity.userId = userId;
        entity.joinedAt = joinedAt;
        entity.lastReadMessageId = lastReadMessageId;
        return entity;
    }

    /** 새 대화방의 참여자 행 */
    public static ConversationMemberJpaEntity of(long conversationId, ConversationMember member) {
        ConversationMemberJpaEntity entity = new ConversationMemberJpaEntity();
        entity.conversationId = conversationId;
        entity.userId = member.getUserId();
        entity.lastReadMessageId = member.getLastReadMessageId();
        entity.joinedAt = member.getJoinedAt();
        entity.leftAt = member.getLeftAt();
        return entity;
    }

    public ConversationMember toDomain() {
        return ConversationMember.builder()
                .id(id)
                .userId(userId)
                .lastReadMessageId(lastReadMessageId)
                .joinedAt(joinedAt)
                .leftAt(leftAt)
                .muted(muted)
                .build();
    }
}
