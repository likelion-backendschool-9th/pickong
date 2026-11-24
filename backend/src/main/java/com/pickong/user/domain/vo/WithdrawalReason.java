package com.pickong.user.domain.vo;

import java.util.Locale;
import java.util.Optional;

/** 탈퇴 사유 (탈퇴 화면의 선택지와 같음). ETC(기타)는 내용을 꼭 적어야 합니다. */
public enum WithdrawalReason {
    RARELY_USED,
    MISSING_FEATURE,
    PRIVACY,
    OTHER_SERVICE,
    ETC;

    /** 대소문자를 가리지 않고 바꿉니다. 모르는 값 · null이면 빈 값 */
    public static Optional<WithdrawalReason> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
