package com.pickong.feed.adapter.in.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommentLikeRequest {

    @NotNull(message = "userId는 필수입니다")
    @Positive(message = "userId는 양수여야 합니다")
    private Long userId;
}
