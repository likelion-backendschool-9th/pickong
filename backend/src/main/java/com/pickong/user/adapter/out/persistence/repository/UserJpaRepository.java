package com.pickong.user.adapter.out.persistence.repository;

import com.pickong.user.adapter.out.persistence.entity.UserJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserJpaEntity u where u.id = :id")
    Optional<UserJpaEntity> findByIdForUpdate(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    // NULL 만료일도 같은 값으로 비교합니다. 단독 ':previousUntil is null'의 PostgreSQL 타입 추론 문제를 피합니다.
    @Query(value = """
            update users
               set status = :status, suspended_until = :until, updated_at = :updatedAt
             where id = :id and status = :previousStatus and deleted_at is null
               and suspended_until is not distinct from :previousUntil
            """, nativeQuery = true)
    int updateStatusIfUnchanged(@Param("id") Long id, @Param("previousStatus") String previousStatus,
            @Param("previousUntil") OffsetDateTime previousUntil, @Param("status") String status,
            @Param("until") OffsetDateTime until, @Param("updatedAt") OffsetDateTime updatedAt);

    /** username(대소문자 무시)으로 회원을 조회합니다. */
    @Query("select u from UserJpaEntity u where lower(u.username) = lower(:username)")
    java.util.Optional<UserJpaEntity> findByUsernameIgnoreCase(@Param("username") String username);

    /** lower(username) UNIQUE 인덱스와 같은 기준으로 비교합니다. */
    @Query("select count(u) > 0 from UserJpaEntity u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    /** lower(email) UNIQUE 인덱스와 같은 기준으로 비교합니다. */
    @Query("select count(u) > 0 from UserJpaEntity u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    /**
     * 프로필 항목만 바꿉니다. 회원 행 전체를 덮어쓰지 않아서, 같은 순간 다른 모듈이 바꾼
     * 팔로워 수 · 게시물 수 같은 집계 컬럼이 옛 값으로 되돌아가지 않습니다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update UserJpaEntity u
               set u.username = :username, u.displayName = :displayName, u.bio = :bio, u.region = :region,
                   u.usernameChangedAt = :usernameChangedAt, u.updatedAt = :updatedAt
             where u.id = :id
            """)
    int updateProfile(@Param("id") Long id, @Param("username") String username, @Param("displayName") String displayName,
            @Param("bio") String bio, @Param("region") String region,
            @Param("usernameChangedAt") OffsetDateTime usernameChangedAt, @Param("updatedAt") OffsetDateTime updatedAt);

    /** 탈퇴 상태만 바꿉니다(신청 · 취소). 집계 컬럼을 덮어쓰지 않으려고 행 전체를 저장하지 않습니다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update UserJpaEntity u
               set u.status = :status, u.withdrawalRequestedAt = :withdrawalRequestedAt, u.updatedAt = :updatedAt
             where u.id = :id
            """)
    int updateWithdrawal(@Param("id") Long id, @Param("status") String status,
            @Param("withdrawalRequestedAt") OffsetDateTime withdrawalRequestedAt, @Param("updatedAt") OffsetDateTime updatedAt);

    /** 익명화한 값만 바꿉니다. 개인정보 컬럼을 비우고 아이디를 내부 값으로 바꿉니다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update UserJpaEntity u
               set u.username = :username, u.email = null, u.passwordHash = null, u.displayName = null, u.bio = null,
                   u.region = null, u.profileImageUrl = null, u.status = :status, u.deletedAt = :deletedAt,
                   u.updatedAt = :updatedAt
             where u.id = :id
            """)
    int updateAnonymized(@Param("id") Long id, @Param("username") String username, @Param("status") String status,
            @Param("deletedAt") OffsetDateTime deletedAt, @Param("updatedAt") OffsetDateTime updatedAt);

    /** 탈퇴 중이고 신청 시각이 threshold 이전인 회원 id (신청 시각 순) */
    @Query("""
            select u.id from UserJpaEntity u
             where u.status = 'WITHDRAWN_PENDING' and u.withdrawalRequestedAt < :threshold
             order by u.withdrawalRequestedAt, u.id
            """)
    java.util.List<Long> findWithdrawalIdsRequestedBefore(@Param("threshold") OffsetDateTime threshold);

    @Modifying
    @Query("UPDATE UserJpaEntity u SET u.followerCount = u.followerCount + 1 WHERE u.id = :userId")
    void incrementFollowerCount(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE UserJpaEntity u SET u.followerCount = u.followerCount - 1 WHERE u.id = :userId AND u.followerCount > 0")
    void decrementFollowerCount(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE UserJpaEntity u SET u.followingCount = u.followingCount + 1 WHERE u.id = :userId")
    void incrementFollowingCount(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE UserJpaEntity u SET u.followingCount = u.followingCount - 1 WHERE u.id = :userId AND u.followingCount > 0")
    void decrementFollowingCount(@Param("userId") Long userId);
}
