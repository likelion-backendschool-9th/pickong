package com.pickong.conversation.adapter.out.persistence.repository;

import com.pickong.conversation.adapter.out.persistence.entity.ConversationMemberJpaEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationMemberJpaRepository extends JpaRepository<ConversationMemberJpaEntity, Long> {

    List<ConversationMemberJpaEntity> findByConversationIdOrderByIdAsc(Long conversationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ConversationMemberJpaEntity m set m.leftAt = null, m.joinedAt = :joinedAt "
            + "where m.id = :id and m.leftAt is not null")
    int rejoin(@Param("id") long id, @Param("joinedAt") OffsetDateTime joinedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ConversationMemberJpaEntity m set m.leftAt = :leftAt where m.id = :id and m.leftAt is null")
    int leave(@Param("id") long id, @Param("leftAt") OffsetDateTime leftAt);

    /** 지금 값보다 클 때만 바꿔서, 동시에 들어온 읽음 요청끼리도 뒤로 가지 않습니다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ConversationMemberJpaEntity m set m.lastReadMessageId = :messageId "
            + "where m.id = :id and (m.lastReadMessageId is null or m.lastReadMessageId < :messageId)")
    int advanceLastRead(@Param("id") long id, @Param("messageId") long messageId);
}
