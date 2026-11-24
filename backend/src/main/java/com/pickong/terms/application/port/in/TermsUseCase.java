package com.pickong.terms.application.port.in;

import java.util.Set;

/** 약관 유스케이스. (blueprint 4.3: 기능이 작은 모듈은 in-port 하나) */
public interface TermsUseCase {

    /**
     * 가입할 때의 약관 동의를 기록합니다. 지금 시행 중인 버전으로 기록하고, 호출한 쪽(가입 완료)의 트랜잭션에 합류합니다.
     * 필수 약관이 하나라도 빠지면 아무것도 기록하지 않고 오류를 던집니다.
     *
     * @param userId      새 회원 id
     * @param agreedTypes 동의한 약관 종류 (SERVICE · PRIVACY · MARKETING). 동의하지 않은 선택 약관은 기록하지 않습니다
     * @throws com.pickong.common.error.BusinessException 400 REQUIRED_TERMS_NOT_AGREED · UNKNOWN_TERMS
     */
    void agreeOnSignup(Long userId, Set<String> agreedTypes);
}
