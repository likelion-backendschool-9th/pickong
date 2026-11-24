package com.pickong.user.adapter.out.persistence.repository;

import com.pickong.user.adapter.out.persistence.entity.UserJpaEntity;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

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
