package com.pickong.social.adapter.in.web;

import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.social.application.port.in.RelationCommandUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 팔로우 · 언팔로우. 로그인 필수
 * 팔로우하는 사람은 요청 값이 아니라 로그인 사용자로 정함 (요청으로 받으면 남의 계정으로 팔로우시킬 수 있음)
 */
@RestController
@RequestMapping("/users/{username}")
public class SocialController {

    private final RelationCommandUseCase relationCommandUseCase;

    public SocialController(RelationCommandUseCase relationCommandUseCase) {
        this.relationCommandUseCase = relationCommandUseCase;
    }

    // 팔로우 — 201 Created. 자기 자신 400, 이미 팔로우 중 409, 없는 회원 404
    @PostMapping("/follow")
    @ResponseStatus(HttpStatus.CREATED)
    public void follow(@PathVariable String username, @CurrentUser LoginUser user) {
        relationCommandUseCase.follow(user.userId(), username);
    }

    // 언팔로우 — 204 No Content. 팔로우 관계가 없으면 404
    @DeleteMapping("/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@PathVariable String username, @CurrentUser LoginUser user) {
        relationCommandUseCase.unfollow(user.userId(), username);
    }
}
