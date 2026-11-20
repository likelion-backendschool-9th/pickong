package com.pickong.user.domain.model;

import com.pickong.user.domain.vo.UserStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 회원 애그리거트. ACTIVE · SUSPENDED · WITHDRAWN_PENDING · WITHDRAWN 전이와 탈퇴 익명화.
 * <p>email · passwordHash는 소셜 전용 계정이면 없습니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {
    private Long id;
    private UUID publicId;
    private String username;
    private String email;
    private String passwordHash;
    private String displayName;
    private String bio;
    private String profileImageUrl;
    private UserStatus status;
    private OffsetDateTime suspendedUntil;
    private long followerCount;
    private long followingCount;
    private long feedCount;
    private OffsetDateTime withdrawalRequestedAt;
    private OffsetDateTime deletedAt;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
