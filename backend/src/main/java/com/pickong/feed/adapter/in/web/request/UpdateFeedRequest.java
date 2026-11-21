package com.pickong.feed.adapter.in.web.request;

import com.pickong.feed.application.dto.UpdateFeedCommand;
import java.util.List;
import com.pickong.common.domain.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class UpdateFeedRequest {
    @NotBlank(message = "내용은 필수입니다.")
    private final String content;
    @NotNull(message = "공개 범위는 필수입니다.")
    private final Visibility visibility;
    private final List<Long> mediaFileIds;
    private final List<Long> placeIds;

    public UpdateFeedCommand toCommand() {
        return new UpdateFeedCommand(content, visibility, mediaFileIds, placeIds);
    }
}