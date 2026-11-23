package com.pickong.feed.adapter.in.web.request;

import com.pickong.common.domain.Visibility;
import java.util.List;
import com.pickong.feed.application.dto.CreateFeedCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class CreateFeedRequest {
    // TODO(JWT): JWT 도입 후 이 필드 제거 — userId는 SecurityContext에서 꺼내 써야됨
    private Long userId;
    @NotBlank(message = "내용은 필수입니다.")
    private String content;
    @NotNull(message = "공개 범위는 필수입니다.")
    private Visibility visibility;
    private List<Long> mediaFileIds;
    private List<Long> placeIds;

    public CreateFeedCommand toCommand() {
        return new CreateFeedCommand(userId, content, visibility, mediaFileIds, placeIds);
    }
}
