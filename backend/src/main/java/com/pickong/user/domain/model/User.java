package com.pickong.user.domain.model;

import com.pickong.common.error.BusinessException;
import com.pickong.user.domain.UserErrorCode;
import com.pickong.user.domain.vo.UserStatus;
import com.pickong.user.domain.vo.Username;
import java.time.Duration;
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
@Builder(toBuilder = true)
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
    /** 활동 지역 (자유 입력, 없으면 null) */
    private String region;
    private String profileImageUrl;
    private UserStatus status;
    private OffsetDateTime suspendedUntil;
    private long followerCount;
    private long followingCount;
    private long feedCount;
    private OffsetDateTime withdrawalRequestedAt;
    private OffsetDateTime deletedAt;
    private OffsetDateTime lastLoginAt;
    /** 마지막으로 아이디를 바꾼 시각. 가입 후 바꾼 적이 없으면 null */
    private OffsetDateTime usernameChangedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** 이름(display_name) 최대 길이. 프론트(entities/user/name.ts)와 같음 */
    public static final int NAME_MAX = 20;
    /** 소개 최대 길이. 프론트 프로필 편집 입력 제한과 같음 */
    public static final int BIO_MAX = 150;
    /** 활동 지역 최대 길이. 프론트 프로필 편집 입력 제한과 같음 */
    public static final int REGION_MAX = 20;
    /** 아이디를 다시 바꿀 수 있기까지의 간격 */
    public static final Duration USERNAME_CHANGE_INTERVAL = Duration.ofDays(14);
    /** 탈퇴 신청 뒤 익명화하기까지의 유예 기간. 이 안에 다시 로그인하면 탈퇴를 취소할 수 있습니다 */
    public static final Duration WITHDRAWAL_GRACE_PERIOD = Duration.ofDays(30);

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
        return normalizeText(name, NAME_MAX, UserErrorCode.INVALID_NAME);
    }

    /**
     * 아이디를 바꾼 새 회원 값을 돌려줍니다. (domain 모델은 값을 직접 바꾸지 않고 새 값을 만듭니다)
     * <ul>
     *   <li>지금 아이디와 같으면 바꾸지 않은 것으로 보고 그대로 돌려줍니다. (프로필 편집은 아이디를 늘 함께 보내므로,
     *       다른 항목만 고쳤는데 14일 제한이 시작되면 안 됨)</li>
     *   <li>마지막 변경 후 14일이 지나지 않았으면 USERNAME_CHANGE_TOO_SOON</li>
     * </ul>
     * 다른 회원이 쓰는지 · 예약어인지는 저장소를 봐야 해서 서비스가 확인합니다.
     */
    public User changeUsername(Username newUsername, OffsetDateTime now) {
        if (newUsername.value().equals(username)) {
            return this;
        }
        OffsetDateTime changeableAt = usernameChangeableAt();
        if (changeableAt != null && now.isBefore(changeableAt)) {
            throw new BusinessException(UserErrorCode.USERNAME_CHANGE_TOO_SOON);
        }
        return toBuilder().username(newUsername.value()).usernameChangedAt(now).updatedAt(now).build();
    }

    /** 다음에 아이디를 바꿀 수 있는 시각. 바꾼 적이 없으면 null(지금 바로 가능) */
    public OffsetDateTime usernameChangeableAt() {
        return usernameChangedAt == null ? null : usernameChangedAt.plus(USERNAME_CHANGE_INTERVAL);
    }

    /** 이름을 바꿉니다. null · 빈 값이면 이름을 지웁니다(화면에는 아이디가 대신 보임). 규칙은 가입과 같습니다. */
    public User rename(String name, OffsetDateTime now) {
        return toBuilder().displayName(normalizeName(name)).updatedAt(now).build();
    }

    /** 소개를 바꿉니다. 앞뒤 공백 정리 → 비면 null → 150자 초과면 INVALID_BIO (줄바꿈은 그대로 둠) */
    public User changeBio(String bio, OffsetDateTime now) {
        return toBuilder().bio(normalizeText(bio, BIO_MAX, UserErrorCode.INVALID_BIO)).updatedAt(now).build();
    }

    /** 활동 지역을 바꿉니다. 앞뒤 공백 정리 → 비면 null → 20자 초과면 INVALID_REGION */
    public User changeRegion(String region, OffsetDateTime now) {
        return toBuilder().region(normalizeText(region, REGION_MAX, UserErrorCode.INVALID_REGION)).updatedAt(now).build();
    }

    /** 자유 입력 칸 공통 규칙: 앞뒤 공백 정리 → 비면 null → 최대 글자 수(코드 포인트 기준) 초과면 오류 */
    private static String normalizeText(String value, int max, UserErrorCode tooLong) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.codePointCount(0, trimmed.length()) > max) {
            throw new BusinessException(tooLong);
        }
        return trimmed;
    }

    /**
     * 탈퇴를 신청합니다: WITHDRAWN_PENDING + 신청 시각. 30일 뒤 익명화되고, 그 전에는 취소할 수 있습니다.
     * ACTIVE 회원만 신청할 수 있습니다(정지 · 이미 탈퇴 중이면 WITHDRAWAL_NOT_ALLOWED).
     */
    public User requestWithdrawal(OffsetDateTime now) {
        if (status != UserStatus.ACTIVE) {
            throw new BusinessException(UserErrorCode.WITHDRAWAL_NOT_ALLOWED);
        }
        return toBuilder().status(UserStatus.WITHDRAWN_PENDING).withdrawalRequestedAt(now).updatedAt(now).build();
    }

    /** 익명화될 시각(신청 + 30일). 탈퇴 중이 아니면 null */
    public OffsetDateTime anonymizeAt() {
        return status == UserStatus.WITHDRAWN_PENDING && withdrawalRequestedAt != null
                ? withdrawalRequestedAt.plus(WITHDRAWAL_GRACE_PERIOD)
                : null;
    }

    /** 탈퇴를 취소할 수 있는지: 탈퇴 중이고 30일이 아직 지나지 않음 (지났으면 익명화 전이라도 취소할 수 없음) */
    public boolean canCancelWithdrawal(OffsetDateTime now) {
        OffsetDateTime anonymizeAt = anonymizeAt();
        return anonymizeAt != null && now.isBefore(anonymizeAt);
    }

    /** 탈퇴를 취소합니다: ACTIVE로 돌아오고 신청 시각을 지웁니다. 취소할 수 없으면 WITHDRAWAL_NOT_CANCELLABLE */
    public User cancelWithdrawal(OffsetDateTime now) {
        if (!canCancelWithdrawal(now)) {
            throw new BusinessException(UserErrorCode.WITHDRAWAL_NOT_CANCELLABLE);
        }
        return toBuilder().status(UserStatus.ACTIVE).withdrawalRequestedAt(null).updatedAt(now).build();
    }

    /** 익명화할 때가 됐는지: 탈퇴 중이고 30일이 지남 */
    public boolean isDueForAnonymization(OffsetDateTime now) {
        OffsetDateTime anonymizeAt = anonymizeAt();
        return anonymizeAt != null && !now.isBefore(anonymizeAt);
    }

    /**
     * 익명화합니다: 이름 · 이메일 · 사진 · 소개 · 활동 지역을 지우고 아이디를 {@code deleted_{id}}로 바꿔 원래 아이디를 풀어 줍니다.
     * 상태는 WITHDRAWN, 익명화 시각은 deleted_at. 행은 남깁니다(댓글 · 신고 등이 이 회원을 가리킴).
     * {@code deleted_}로 시작하는 아이디는 예약어라({@link Username}) 다른 회원과 겹치지 않습니다.
     */
    public User anonymize(OffsetDateTime now) {
        if (!isDueForAnonymization(now)) {
            throw new IllegalStateException("익명화할 때가 아닌 회원입니다: " + id);
        }
        return toBuilder()
                .username(Username.ANONYMIZED_PREFIX + id)
                .email(null)
                .passwordHash(null)
                .displayName(null)
                .bio(null)
                .region(null)
                .profileImageUrl(null)
                .status(UserStatus.WITHDRAWN)
                .deletedAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 로그인(토큰 재발급 포함)을 허용하는 상태인지. 지금은 ACTIVE만 허용합니다.
     * (정지 회원이 무엇을 할 수 있는지는 blueprint 8.5 #7에서 정해지면 바꿉니다)
     */
    public boolean canLogin() {
        return status == UserStatus.ACTIVE;
    }
}
