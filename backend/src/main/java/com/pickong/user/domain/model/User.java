package com.pickong.user.domain.model;

import com.pickong.common.error.BusinessException;
import com.pickong.user.domain.UserErrorCode;
import com.pickong.user.domain.vo.UserStatus;
import com.pickong.user.domain.vo.Username;
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

    /** 이름(display_name) 최대 길이. 프론트(entities/user/name.ts)와 같음 */
    public static final int NAME_MAX = 20;

    /**
     * 온보딩을 마친 신규 회원을 만듭니다. (id는 저장할 때 DB가 채움)
     * <ul>
     *   <li>이름: 앞뒤 공백을 지우고, 비어 있으면 null(화면에는 닉네임이 대신 보임), 20자 초과는 INVALID_NAME</li>
     *   <li>공개 ID(public_id)와 생성 시각은 여기서 정합니다. (JPA가 null을 그대로 INSERT해서 DB 기본값이 쓰이지 않기 때문)</li>
     *   <li>상태는 ACTIVE, 카운터는 0</li>
     * </ul>
     *
     * @param email           가입한 소셜 계정의 인증된 이메일 (없으면 null)
     * @param profileImageUrl 소셜 프로필 사진을 쓰기로 했을 때만 값 (없으면 null)
     */
    public static User register(Username username, String name, String email, String profileImageUrl, OffsetDateTime now) {
        return User.builder()
                .publicId(UUID.randomUUID())
                .username(username.value())
                .email(email)
                .displayName(normalizeName(name))
                .profileImageUrl(profileImageUrl)
                .status(UserStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** 이름 규칙: 공백 정리 → 비면 null → 20자(코드 포인트 기준, 한글 · 이모지 한 글자 = 1) 초과면 오류 */
    static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > NAME_MAX) {
            throw new BusinessException(UserErrorCode.INVALID_NAME);
        }
        return trimmed;
    }

    /**
     * 로그인(토큰 재발급 포함)을 허용하는 상태인지. 지금은 ACTIVE만 허용합니다.
     * (정지 회원이 무엇을 할 수 있는지는 blueprint 8.5 #7에서 정해지면 바꿉니다)
     */
    public boolean canLogin() {
        return status == UserStatus.ACTIVE;
    }
}
