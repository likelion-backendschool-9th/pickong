package com.pickong.terms.application.port.in;

import com.pickong.terms.application.dto.AgreementStatus;
import com.pickong.terms.application.dto.TermsDocument;
import java.util.List;
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

    /**
     * 지금 시행 중인 약관 전문(조항 포함). 로그인 없이 볼 수 있습니다(가입 화면의 '보기').
     *
     * @param type 약관 종류. 대소문자를 가리지 않습니다 (service · SERVICE)
     * @throws com.pickong.common.error.BusinessException 404 TERMS_NOT_FOUND
     */
    TermsDocument getCurrentTerms(String type);

    /**
     * 내 약관 동의 상태. 지금 시행 중인 약관마다 한 줄씩, 종류 순서(SERVICE · PRIVACY · MARKETING)로 돌려줍니다.
     */
    List<AgreementStatus> getMyAgreements(Long userId);

    /**
     * 선택 약관(마케팅)에 동의하거나 철회하고, 바뀐 동의 상태 전체를 돌려줍니다.
     * 이미 같은 상태면 아무것도 바꾸지 않습니다(같은 요청을 여러 번 보내도 결과가 같음).
     *
     * @throws com.pickong.common.error.BusinessException 400 UNKNOWN_TERMS · REQUIRED_TERMS_CANNOT_BE_REVOKED
     */
    List<AgreementStatus> updateAgreement(Long userId, String type, boolean agreed);
}
