package com.pickong.auth.adapter.in.event;

import com.pickong.auth.application.port.in.SocialAccountUseCase;
import com.pickong.auth.application.port.in.TokenUseCase;
import com.pickong.user.domain.event.UserWithdrawnEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 회원 익명화(UserWithdrawnEvent)를 받아 auth가 가진 그 회원의 데이터를 지웁니다: 소셜 연결 · Refresh Token.
 * <p>
 * <b>동기 · 같은 트랜잭션</b>(@EventListener)입니다. 여기서 실패하면 익명화까지 되돌아가 회원이 탈퇴 중으로 남고,
 * 다음 날 예약 작업이 다시 처리합니다. 그래서 '익명화됐는데 소셜 연결이 남아 같은 계정으로 다시 가입할 수 없음' 상태가 생기지 않습니다.
 * 두 삭제 모두 이미 없으면 아무것도 하지 않아 여러 번 불려도 결과가 같습니다.
 */
@Component
public class UserWithdrawnListener {

    private final SocialAccountUseCase socialAccounts;
    private final TokenUseCase tokens;

    public UserWithdrawnListener(SocialAccountUseCase socialAccounts, TokenUseCase tokens) {
        this.socialAccounts = socialAccounts;
        this.tokens = tokens;
    }

    @EventListener
    public void on(UserWithdrawnEvent event) {
        socialAccounts.unlinkAll(event.userId());
        tokens.deleteAllTokens(event.userId());
    }
}
