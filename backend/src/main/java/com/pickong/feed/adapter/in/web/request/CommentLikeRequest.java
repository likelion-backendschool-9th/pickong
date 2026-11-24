package com.pickong.feed.adapter.in.web.request;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommentLikeRequest {

    @Positive(message = "userId는 양수여야 합니다")
    private Long userId;
}
