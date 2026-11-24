package com.pickong.user.adapter.in.web;

import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.user.adapter.in.web.request.UpdateMeRequest;
import com.pickong.user.adapter.in.web.response.MeResponse;
import com.pickong.user.application.port.in.UserCommandUseCase;
import com.pickong.user.application.port.in.UserQueryUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 정보 API. 로그인이 필요하고, PATCH는 CSRF 헤더(X-XSRF-TOKEN)가 필요합니다. */
@RestController
@RequestMapping("/me")
public class MeController {

    private final UserQueryUseCase userQueryUseCase;
    private final UserCommandUseCase userCommandUseCase;

    public MeController(UserQueryUseCase userQueryUseCase, UserCommandUseCase userCommandUseCase) {
        this.userQueryUseCase = userQueryUseCase;
        this.userCommandUseCase = userCommandUseCase;
    }

    /** GET /api/v1/me — 내 정보 */
    @GetMapping
    public MeResponse me(@CurrentUser LoginUser user) {
        return MeResponse.from(userQueryUseCase.getMe(user.userId()));
    }

    /**
     * PATCH /api/v1/me — 보낸 항목만 바꾸고 바뀐 내 정보를 돌려줍니다.
     * <ul>
     *   <li>400 INVALID_USERNAME · INVALID_NAME · INVALID_BIO · INVALID_REGION</li>
     *   <li>409 USERNAME_TAKEN — 사용 중 · 예약어</li>
     *   <li>409 USERNAME_CHANGE_TOO_SOON — 마지막 아이디 변경 후 14일 이내</li>
     * </ul>
     * 아이디를 바꿔도 로그인은 유지됩니다(토큰에는 아이디가 아니라 회원 번호가 들어 있음).
     */
    @PatchMapping
    public MeResponse update(@CurrentUser LoginUser user, @RequestBody UpdateMeRequest request) {
        return MeResponse.from(userCommandUseCase.updateProfile(user.userId(), request.toCommand()));
    }
}
