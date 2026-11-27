package com.pickong.conversation.adapter.out.persistence.repository;

import com.pickong.conversation.adapter.out.persistence.entity.MessageJpaEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** "볼 수 있는 메시지" = 지우지 않았고 참여 시각(visibleFrom) 이후에 보낸 메시지 */
public interface MessageJpaRepository extends JpaRepository<MessageJpaEntity, Long> {

    @Query("select max(m.id) from MessageJpaEntity m where m.conversationId = :conversationId "
            + "and m.deletedAt is null and m.createdAt >= :visibleFrom")
    Optional<Long> findLatestVisibleId(@Param("conversationId") long conversationId,
                                       @Param("visibleFrom") OffsetDateTime visibleFrom);

    @Query("select count(m) > 0 from MessageJpaEntity m where m.id = :id and m.conversationId = :conversationId "
            + "and m.deletedAt is null and m.createdAt >= :visibleFrom")
    boolean existsVisible(@Param("conversationId") long conversationId, @Param("id") long id,
                          @Param("visibleFrom") OffsetDateTime visibleFrom);
}
