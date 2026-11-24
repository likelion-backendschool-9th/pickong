package com.pickong.user.adapter.in.web;

import com.pickong.user.adapter.in.web.response.UsernameAvailabilityResponse;
import com.pickong.user.application.port.in.UserQueryUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 닉네임(= 사용자 ID) 중복 확인. 경로는 /auth 아래지만 users.username의 규칙과 UNIQUE를 user 모듈이 가지므로
 * 컨트롤러도 user 모듈에 둡니다. (blueprint 2.2)
 * 온보딩 중에는 아직 로그인 전이라 공개 경로(/auth/**)에 있습니다.
 */
@RestController
public class UsernameController {

    private final UserQueryUseCase userQueryUseCase;

    public UsernameController(UserQueryUseCase userQueryUseCase) {
        this.userQueryUseCase = userQueryUseCase;
    }

    /**
     * GET /api/v1/auth/username-availability?username=seoyeon
     * <ul>
     *   <li>200 {@code {"available": true}} — 쓸 수 있음</li>
     *   <li>200 {@code {"available": false}} — 누가 쓰고 있거나 예약어 (대소문자 무시)</li>
     *   <li>400 INVALID_USERNAME — 형식 오류 (프론트가 먼저 걸러서 보통은 오지 않음)</li>
     * </ul>
     */
    @GetMapping("/auth/username-availability")
    public UsernameAvailabilityResponse availability(@RequestParam String username) {
        return new UsernameAvailabilityResponse(userQueryUseCase.isUsernameAvailable(username));
    }
}
