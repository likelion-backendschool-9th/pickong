package com.pickong.auth.adapter.in.web.request;

import java.util.List;

/**
 * POST /auth/signup 요청 본문 (프론트 signupApi.ts의 CompleteSignupInput과 같은 모양).
 * <pre>{@code
 * { "username": "seoyeon", "name": "서연", "useSocialProfileImage": true, "agreedTerms": ["SERVICE", "PRIVACY"] }
 * }</pre>
 * 형식 검증은 user · terms 모듈의 규칙이 하므로 여기서는 받기만 합니다. (규칙을 한 곳에만 두기 위해서)
 */
public record SignUpRequest(String username, String name, boolean useSocialProfileImage, List<String> agreedTerms) {
}
