package com.pickong.conversation.adapter.out.persistence.repository;

import com.pickong.conversation.adapter.out.persistence.entity.ConversationJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationJpaRepository extends JpaRepository<ConversationJpaEntity, Long> {

    /**
     * 같은 direct_key가 없을 때만 1:1 대화방을 만듭니다. 동시에 같은 키로 들어오면 먼저 들어온 쪽이 커밋할 때까지 기다린 뒤
     * 0을 돌려받으므로, 트랜잭션을 깨는 UNIQUE 위반 예외 없이 기존 대화방을 다시 읽을 수 있습니다.
     *
     * @return 만들었으면 1, 이미 있었으면 0
     */
    @Modifying
    @Query(value = """
            insert into conversations (type, direct_key, created_by, created_at, updated_at)
            values ('DIRECT', :directKey, :createdBy, :now, :now)
            on conflict (direct_key) do nothing
            """, nativeQuery = true)
    int insertDirectIfAbsent(@Param("directKey") String directKey, @Param("createdBy") long createdBy,
                             @Param("now") OffsetDateTime now);

    Optional<ConversationJpaEntity> findByDirectKey(String directKey);

    /** SELECT … FOR UPDATE: 메시지 저장 전에 대화방 행을 잠급니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConversationJpaEntity c where c.id = :id")
    Optional<ConversationJpaEntity> findByIdForUpdate(@Param("id") long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ConversationJpaEntity c set c.updatedAt = :at where c.id = :id")
    int touch(@Param("id") long id, @Param("at") OffsetDateTime at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ConversationJpaEntity c set c.title = :title where c.id = :id and c.type = 'GROUP'")
    int rename(@Param("id") long id, @Param("title") String title);
}
