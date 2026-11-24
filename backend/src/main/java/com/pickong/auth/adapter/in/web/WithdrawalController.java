package com.pickong.auth.adapter.in.web;

import com.pickong.auth.adapter.in.security.AuthCookies;
import com.pickong.auth.adapter.in.security.TokenCookieWriter;
import com.pickong.auth.adapter.in.web.request.WithdrawalRequest;
import com.pickong.auth.adapter.in.web.response.RestoreSessionResponse;
import com.pickong.auth.adapter.in.web.response.RestoredAccountResponse;
import com.pickong.auth.application.dto.RestoredAccount;
import com.pickong.auth.application.port.in.AccountWithdrawalUseCase;
import com.pickong.auth.application.port.in.TokenUseCase;
import com.pickong.auth.domain.AuthErrorCode;
import com.pickong.common.error.BusinessException;
import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.user.domain.UserErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 탈퇴 API. 신청은 로그인한 회원이, 취소는 유예 중 로그인해 탈퇴 취소 쿠키를 받은 회원이 합니다.
 * 회원 상태는 user가 바꾸고, 세션 · 쿠키는 auth가 다뤄야 해서 이 컨트롤러가 auth에 있습니다(가입 완료와 같은 방식).
 * 모든 쓰기 요청은 CSRF 헤더가 필요합니다.
 */
@RestController
public class WithdrawalController {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalController.class);

    private final AccountWithdrawalUseCase withdrawal;
    private final TokenUseCase tokenUseCase;
    private final TokenCookieWriter cookieWriter;

    public WithdrawalController(AccountWithdrawalUseCase withdrawal, TokenUseCase tokenUseCase, TokenCookieWriter cookieWriter) {
        this.withdrawal = withdrawal;
        this.tokenUseCase = tokenUseCase;
        this.cookieWriter = cookieWriter;
    }

    /**
     * POST /api/v1/me/withdrawal — 탈퇴 신청. 성공하면 모든 기기의 세션을 폐기하고 이 브라우저의 쿠키를 지운 뒤 204.
     * <ul>
     *   <li>400 INVALID_WITHDRAWAL_REASON — 사유가 없거나 모름 · 기타인데 내용 없음 · 300자 초과</li>
     *   <li>409 WITHDRAWAL_NOT_ALLOWED — 이미 탈퇴 중 · 정지</li>
     * </ul>
     * 세션 폐기는 탈퇴 신청이 커밋된 뒤에 합니다. 혹시 폐기가 실패해도 탈퇴 중인 회원은 토큰 재발급이 거절됩니다.
     */
    @PostMapping("/me/withdrawal")
    public ResponseEntity<Void> request(@CurrentUser LoginUser user, @RequestBody WithdrawalRequest request,
                                        HttpServletResponse response) {
        withdrawal.requestWithdrawal(user.userId(), request.reason(), request.detail());
        tokenUseCase.revokeAllSessions(user.userId());
        cookieWriter.clear(response);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/auth/withdrawal — 탈퇴 취소 화면의 탈퇴 상태.
     * <ul>
     *   <li>200 {@code {"username":"seoyeon","requestedAt":"…","anonymizeAt":"…"}}</li>
     *   <li>401 RESTORE_SESSION_EXPIRED — 탈퇴 취소 쿠키가 없거나 만료 (쿠키도 지움) → 프론트는 로그인 화면으로</li>
     *   <li>409 WITHDRAWAL_NOT_CANCELLABLE — 30일이 지남</li>
     * </ul>
     */
    @GetMapping("/auth/withdrawal")
    public RestoreSessionResponse session(@CookieValue(name = AuthCookies.RESTORE_TOKEN, required = false) String restoreToken,
                                          HttpServletResponse response) {
        try {
            return RestoreSessionResponse.from(withdrawal.getRestoreSession(restoreToken));
        } catch (BusinessException e) {
            clearRestoreCookieIfExpired(e, response);
            throw e;
        }
    }

    /**
     * POST /api/v1/auth/withdrawal/cancel — 탈퇴 취소. 성공하면 ACTIVE로 돌리고 세션 쿠키를 발급, 탈퇴 취소 쿠키를 지운 뒤
     * 200 {@code {"username":"seoyeon"}}. 오류는 GET과 같습니다.
     */
    @PostMapping("/auth/withdrawal/cancel")
    public RestoredAccountResponse cancel(@CookieValue(name = AuthCookies.RESTORE_TOKEN, required = false) String restoreToken,
                                 HttpServletResponse response) {
        try {
            RestoredAccount restored = withdrawal.cancelWithdrawal(restoreToken); // 여기서 취소가 커밋됩니다
            issueSession(restored, response);
            cookieWriter.clearRestore(response);
            return new RestoredAccountResponse(restored.username());
        } catch (BusinessException e) {
            clearRestoreCookieIfExpired(e, response);
            throw e;
        }
    }

    /**
     * 취소가 커밋된 뒤 세션을 발급합니다. 여기서 실패하면 회원은 ACTIVE인데 세션 쿠키가 없는 상태가 됩니다.
     * 다시 소셜 로그인하면 정상 세션이 발급되므로 되돌리지 않고, 찾아볼 수 있게 회원 id와 함께 로그만 남긴 뒤 그대로 던집니다(500).
     */
    private void issueSession(RestoredAccount restored, HttpServletResponse response) {
        try {
            cookieWriter.write(response, tokenUseCase.issue(restored.userId()));
        } catch (RuntimeException e) {
            log.error("탈퇴는 취소됐지만 세션 발급 실패 (회원 {}): 다시 로그인하면 세션이 발급됩니다", restored.userId(), e);
            throw e;
        }
    }

    /** DELETE /api/v1/auth/withdrawal — 탈퇴 취소 화면에서 그만두기(탈퇴 유지). 탈퇴 취소 쿠키만 지우고 항상 204. */
    @DeleteMapping("/auth/withdrawal")
    public ResponseEntity<Void> leave(HttpServletResponse response) {
        cookieWriter.clearRestore(response);
        return ResponseEntity.noContent().build();
    }

    /** 만료 · 취소 불가인 탈퇴 취소 쿠키는 남겨 둬도 쓸 수 없으니 지웁니다. */
    private void clearRestoreCookieIfExpired(BusinessException e, HttpServletResponse response) {
        if (e.getErrorCode() == AuthErrorCode.RESTORE_SESSION_EXPIRED
                || e.getErrorCode() == UserErrorCode.WITHDRAWAL_NOT_CANCELLABLE) {
            cookieWriter.clearRestore(response);
        }
    }
}
