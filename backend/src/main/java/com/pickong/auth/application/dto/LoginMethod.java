package com.pickong.auth.application.dto;

import com.pickong.auth.domain.vo.SocialProvider;
import java.time.OffsetDateTime;

/**
 * 로그인 수단 관리 화면의 한 줄.
 *
 * @param canUnlink 해제할 수 있는지. 가입할 때 쓴 수단(가장 먼저 연결된 것)만 false
 */
public record LoginMethod(SocialProvider provider, String email, OffsetDateTime connectedAt, boolean canUnlink) {
}
