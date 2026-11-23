package com.pickong.auth.adapter.in.web;

import com.pickong.auth.adapter.in.security.AuthCookies;
import com.pickong.auth.adapter.in.security.TokenCookieWriter;
import com.pickong.auth.adapter.in.web.request.SignUpRequest;
import com.pickong.auth.adapter.in.web.response.SignUpResponse;
import com.pickong.auth.adapter.in.web.response.SignupSessionResponse;
import com.pickong.auth.application.dto.SignUpCommand;
import com.pickong.auth.application.dto.SignUpResult;
import com.pickong.auth.application.port.in.SignUpUseCase;
import com.pickong.auth.application.port.in.TokenUseCase;
import com.pickong.auth.domain.AuthErrorCode;
import com.pickong.common.error.BusinessException;
import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 최초 가입 온보딩 API. 모두 임시 가입 쿠키(pickong_signup)로 동작하며, 로그인 전이라 공개 경로(/auth/**)입니다.
 * POST · DELETE는 CSRF 헤더(X-XSRF-TOKEN)가 필요합니다.
 */
@RestController
@RequestMapping("/auth/signup")
public class SignupController {

    private final SignUpUseCase signUpUseCase;
    private final TokenUseCase tokenUseCase;
    private final TokenCookieWriter cookieWriter;

    public SignupController(SignUpUseCase signUpUseCase, TokenUseCase tokenUseCase, TokenCookieWriter cookieWriter) {
        this.signUpUseCase = signUpUseCase;
        this.tokenUseCase = tokenUseCase;
        this.cookieWriter = cookieWriter;
    }

    /**
     * GET /api/v1/auth/signup — 진행 중인 가입의 소셜 계정 정보(온보딩 화면 기본값).
     * <ul>
     *   <li>200 {@code {"provider":"KAKAO","name":"콩이","profileImageUrl":"https://...","email":"..."}}</li>
     *   <li>401 SIGNUP_SESSION_EXPIRED — 가입 쿠키가 없거나 만료 (쿠키도 지움) → 프론트는 로그인 화면으로</li>
     *   <li>409 ALREADY_REGISTERED — 가입 쿠키는 없고 이미 로그인한 상태 → 프론트는 피드로</li>
     * </ul>
     * 새로고침 · 재접속 때마다 이 API로 서버의 가입 상태를 다시 확인해서 화면 상태가 어긋나지 않습니다.
     */
    @GetMapping
    public SignupSessionResponse session(
            @CookieValue(name = AuthCookies.SIGNUP_TOKEN, required = false) String signupToken,
            @CurrentUser(required = false) LoginUser currentUser,
            HttpServletResponse response) {
        try {
            return SignupSessionResponse.from(signUpUseCase.getSession(signupToken, currentUser));
        } catch (BusinessException e) {
            clearSignupCookieIfExpired(e, response);
            throw e;
        }
    }

    /**
     * POST /api/v1/auth/signup — 가입 완료.
     * 성공하면 회원 생성 → 세션 쿠키(Access · Refresh) 발급 → 가입 쿠키 삭제 → 200 {@code {"username":"seoyeon"}}.
     * 세션 발급은 가입 트랜잭션이 커밋된 뒤에 합니다. 혹시 발급이 실패해도 회원은 소셜 로그인으로 다시 들어올 수 있습니다.
     */
    @PostMapping
    public SignUpResponse signUp(
            @CookieValue(name = AuthCookies.SIGNUP_TOKEN, required = false) String signupToken,
            @CurrentUser(required = false) LoginUser currentUser,
            @RequestBody SignUpRequest request,
            HttpServletResponse response) {
        try {
            SignUpResult result = signUpUseCase.signUp(signupToken, currentUser, toCommand(request));
            cookieWriter.write(response, tokenUseCase.issue(result.userId()));
            cookieWriter.clearSignup(response);
            return new SignUpResponse(result.username());
        } catch (BusinessException e) {
            clearSignupCookieIfExpired(e, response);
            throw e;
        }
    }

    /** DELETE /api/v1/auth/signup — 가입 그만두기. 가입 쿠키만 지우고 항상 204. (서버에 남은 것이 없음) */
    @DeleteMapping
    public ResponseEntity<Void> cancel(HttpServletResponse response) {
        cookieWriter.clearSignup(response);
        return ResponseEntity.noContent().build();
    }

    private static SignUpCommand toCommand(SignUpRequest request) {
        List<String> agreed = request.agreedTerms() == null ? List.of() : request.agreedTerms();
        return new SignUpCommand(request.username(), request.name(), request.useSocialProfileImage(), new LinkedHashSet<>(agreed));
    }

    /** 만료된 가입 쿠키는 남겨 둬도 쓸 수 없으니 지웁니다. (다른 오류일 때는 다시 시도할 수 있게 그대로 둠) */
    private void clearSignupCookieIfExpired(BusinessException e, HttpServletResponse response) {
        if (e.getErrorCode() == AuthErrorCode.SIGNUP_SESSION_EXPIRED) {
            cookieWriter.clearSignup(response);
        }
    }
}
