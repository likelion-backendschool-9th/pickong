package com.pickong.social.adapter.in.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** JWT 도입 전 임시로 userId를 요청 본문에서 받습니다. */
@Getter
@NoArgsConstructor
public class FollowRequest {

    @NotNull(message = "userId는 필수입니다")
    @Positive(message = "userId는 양수여야 합니다")
    private Long userId;
}
