package com.pickong.user.adapter.in.web;

import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.user.adapter.in.web.response.UserProfileResponse;
import com.pickong.user.application.port.in.UserQueryUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로필 API. 팔로우(/users/{username}/follow)는 social 모듈의 SocialController에 있습니다.
 * <p>
 * 지금은 로그인이 필요합니다. 공개로 열면 Access Token이 만료된 채 내 프로필을 열었을 때
 * 비로그인으로 처리되어 401(→ 재발급)이 오지 않고 남의 프로필(isMe=false)로 보이기 때문입니다.
 */
@RestController
public class UserProfileController {

    private final UserQueryUseCase userQueryUseCase;

    public UserProfileController(UserQueryUseCase userQueryUseCase) {
        this.userQueryUseCase = userQueryUseCase;
    }

    /** GET /api/v1/users/{username} — 프로필. 아이디는 대소문자를 무시합니다. 없거나 정상(ACTIVE) 회원이 아니면 404 USER_NOT_FOUND */
    @GetMapping("/users/{username}")
    public UserProfileResponse profile(@CurrentUser LoginUser viewer, @PathVariable String username) {
        return UserProfileResponse.from(userQueryUseCase.getProfile(viewer.userId(), username));
    }
}
