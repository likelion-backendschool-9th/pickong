package com.pickong.auth.adapter.in.web;

import com.pickong.auth.adapter.in.security.AuthCookies;
import com.pickong.auth.adapter.in.security.TokenCookieWriter;
import com.pickong.auth.application.port.in.TokenUseCase;
import com.pickong.auth.domain.AuthErrorCode;
import com.pickong.common.error.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토큰 재발급 · 로그아웃 API. 토큰은 응답 본문이 아니라 httpOnly 쿠키로만 주고받습니다.
 * 두 API 모두 POST라 CSRF 헤더(X-XSRF-TOKEN)가 필요합니다.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final TokenUseCase tokenUseCase;
    private final TokenCookieWriter cookieWriter;

    public AuthController(TokenUseCase tokenUseCase, TokenCookieWriter cookieWriter) {
        this.tokenUseCase = tokenUseCase;
        this.cookieWriter = cookieWriter;
    }

    /**
     * POST /api/v1/auth/refresh — Refresh Token 쿠키로 새 토큰 쌍을 받습니다.
     * <ul>
     *   <li>성공: 204 + 새 Access · Refresh 쿠키 (쓴 Refresh Token은 폐기됨)</li>
     *   <li>실패: 401 + 두 쿠키 삭제. 프론트는 로그인 화면으로 보냅니다</li>
     * </ul>
     * 실패할 때 쿠키를 지우는 이유: 쓸 수 없는 쿠키가 남아 있으면 브라우저가 계속 보내 같은 실패를 반복하기 때문입니다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
            cookieWriter.write(response, tokenUseCase.refresh(refreshToken));
            return ResponseEntity.noContent().build();
        } catch (BusinessException e) {
            cookieWriter.clear(response);
            throw e; // 오류 본문({status, code, message})은 GlobalExceptionHandler가 씁니다.
        }
    }

    /**
     * POST /api/v1/auth/logout — 이 기기의 로그인 세션을 서버에서 폐기하고 쿠키를 지웁니다.
     * 이미 로그아웃된 상태여도 204입니다. (로그아웃 버튼을 여러 번 눌러도 오류가 나지 않게)
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken,
            HttpServletResponse response) {
        tokenUseCase.logout(refreshToken);
        cookieWriter.clear(response);
        return ResponseEntity.noContent().build();
    }
}
