package com.pickong.auth.application.dto;

import com.pickong.auth.domain.vo.SocialProvider;
import java.time.OffsetDateTime;

/**
 * 내 계정에 연결된 소셜 계정 한 줄. 저장소가 연결 순서(id 오름차순)대로 돌려줍니다.
 *
 * @param id          user_social_accounts.id. 순서 · 해제 대상 지정용
 * @param provider    제공자
 * @param email       이 소셜 계정의 인증된 이메일. 없으면 null
 * @param connectedAt 연결한 시각
 */
public record ConnectedSocialAccount(Long id, SocialProvider provider, String email, OffsetDateTime connectedAt) {
}
