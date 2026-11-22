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
    /** 닉네임 = 사용자 ID (영문, 중복 불가) */
    private String username;
    /** 가입한 소셜 계정의 인증된 이메일 (없으면 null) */
    private String email;
    private String passwordHash;
    /** 이름 (중복 가능, 없으면 null) */
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

    /**
     * 로그인(토큰 재발급 포함)을 허용하는 상태인지. 지금은 ACTIVE만 허용합니다.
     * (정지 회원이 무엇을 할 수 있는지는 blueprint 8.5 #7에서 정해지면 바꿉니다)
     */
    public boolean canLogin() {
        return status == UserStatus.ACTIVE;
    }
}
