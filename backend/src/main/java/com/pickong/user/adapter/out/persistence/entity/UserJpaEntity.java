package com.pickong.user.adapter.out.persistence.entity;

import com.pickong.user.domain.model.User;
import com.pickong.user.domain.vo.UserStatus;
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
 * users 테이블 JPA 엔티티.
 * <p>
 * 헥사고날 규칙: adapter/out/persistence/entity 에만 위치.
 * domain 모델(User.java)과 별개이며, 서비스 계층에 직접 노출하지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class UserJpaEntity {

    // ───────────── PK ─────────────
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ───────────── 식별 ─────────────
    /** 외부 공유용 ID. URL에는 이 값만 노출. */
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    /** 닉네임 = 사용자 ID. 영문 소문자로 저장, 대소문자 무시 UNIQUE (DB에서 lower() 함수 인덱스로 처리). */
    @Column(name = "username", nullable = false, unique = true, length = 20)
    private String username;

    /** 가입한 소셜 계정의 인증된 이메일. 제공자가 주지 않으면 NULL. 대소문자 무시 UNIQUE (1인 1계정). */
    @Column(name = "email", unique = true)
    private String email;

    /** 비밀번호 해시(BCrypt). 소셜 전용 계정은 NULL. */
    @Column(name = "password_hash")
    private String passwordHash;

    // ───────────── 프로필 ─────────────
    /** 이름. 중복 가능, 정하지 않으면 NULL (화면에는 닉네임이 대신 보임). */
    @Column(name = "display_name", length = 30)
    private String displayName;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    // ───────────── 상태 ─────────────
    /**
     * 계정 상태.
     * 값 목록: ACTIVE | SUSPENDED | WITHDRAWN_PENDING | WITHDRAWN
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 정지 해제 일시. SUSPENDED 상태일 때만 값이 있음. */
    @Column(name = "suspended_until")
    private OffsetDateTime suspendedUntil;

    // ───────────── 카운터 (≥ 0 보장) ─────────────
    @Column(name = "follower_count", nullable = false)
    private long followerCount;

    @Column(name = "following_count", nullable = false)
    private long followingCount;

    @Column(name = "feed_count", nullable = false)
    private long feedCount;

    // ───────────── 탈퇴 ─────────────
    /** 탈퇴 신청 일시. WITHDRAWN_PENDING 상태일 때 세팅됨. */
    @Column(name = "withdrawal_requested_at")
    private OffsetDateTime withdrawalRequestedAt;

    /** 익명화 처리 일시. 탈퇴 신청 30일 뒤 처리 후 세팅됨. */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    // ───────────── 감사 ─────────────
    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** DB 행을 domain 모델로 바꿉니다. 상태 문자열은 enum으로 바꿉니다. */
    public User toDomain() {
        return User.builder()
                .id(id)
                .publicId(publicId)
                .username(username)
                .email(email)
                .passwordHash(passwordHash)
                .displayName(displayName)
                .bio(bio)
                .profileImageUrl(profileImageUrl)
                .status(UserStatus.valueOf(status))
                .suspendedUntil(suspendedUntil)
                .followerCount(followerCount)
                .followingCount(followingCount)
                .feedCount(feedCount)
                .withdrawalRequestedAt(withdrawalRequestedAt)
                .deletedAt(deletedAt)
                .lastLoginAt(lastLoginAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
