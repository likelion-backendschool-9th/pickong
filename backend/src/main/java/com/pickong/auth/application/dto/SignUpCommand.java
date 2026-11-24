package com.pickong.auth.application.dto;

import java.util.Set;

/**
 * 온보딩 입력값.
 *
 * @param username              닉네임(= 사용자 ID)
 * @param name                  이름 (비어 있으면 이름 없음)
 * @param useSocialProfileImage true면 소셜 프로필 사진을 씀. 사진 주소는 클라이언트가 보내지 않고 임시 가입 토큰에 있는 값만 씁니다
 * @param agreedTerms           동의한 약관 종류 (SERVICE · PRIVACY · MARKETING)
 */
public record SignUpCommand(String username, String name, boolean useSocialProfileImage, Set<String> agreedTerms) {
}
