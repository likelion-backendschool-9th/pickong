package com.pickong.user.domain.vo;

import com.pickong.common.error.BusinessException;
import com.pickong.user.domain.UserErrorCode;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 닉네임 = 사용자 ID (users.username). 프로필 주소(/users/{닉네임})에 그대로 쓰이는 값입니다.
 * <p>
 * 규칙(프론트 entities/user/nickname.ts와 같음):
 * <ul>
 *   <li>영문으로 시작, 3~20자, 영문 소문자 · 숫자 · 밑줄(_) · 마침표(.)만</li>
 *   <li>마침표는 끝에 쓰거나 연달아 쓸 수 없음</li>
 *   <li>대문자는 소문자로 바꿔 저장 → 대소문자만 다른 닉네임은 같은 닉네임 (DB도 lower(username) UNIQUE)</li>
 * </ul>
 *
 * @param value 규칙을 통과하고 소문자로 맞춘 값
 */
public record Username(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9._]{2,19}$");

    /** 익명화한 회원의 아이디 앞부분 ({@code deleted_42}). 회원이 이 모양으로 가입 · 변경하면 익명화가 겹쳐 실패하므로 예약어로 막습니다 */
    public static final String ANONYMIZED_PREFIX = "deleted_";

    /**
     * 운영자 · 공식 계정으로 착각할 수 있어 회원이 쓸 수 없는 닉네임. '사용 중'으로 처리합니다.
     * (나중에 공식 계정을 만들 수 있도록 비워 둠)
     */
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "root", "system", "staff", "manager", "moderator",
            "pickong", "jjimkong", "official", "support", "help", "notice", "security",
            "api", "me", "settings", "login", "signup", "null", "undefined");

    /**
     * 입력값을 검사해 닉네임을 만듭니다. 앞뒤 공백은 지우고 소문자로 맞춥니다.
     *
     * @throws BusinessException 400 INVALID_USERNAME — 형식이 맞지 않을 때
     */
    public static Username of(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches() || value.endsWith(".") || value.contains("..")) {
            throw new BusinessException(UserErrorCode.INVALID_USERNAME);
        }
        return new Username(value);
    }

    /** 예약어인지. 예약어는 형식은 맞지만 아무도 쓸 수 없습니다. ({@code deleted_}로 시작하는 것 포함) */
    public boolean isReserved() {
        return RESERVED.contains(value) || value.startsWith(ANONYMIZED_PREFIX);
    }
}
