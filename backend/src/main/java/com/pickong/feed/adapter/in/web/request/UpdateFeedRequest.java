package com.pickong.feed.adapter.in.web.request;

import com.pickong.feed.application.dto.UpdateFeedCommand;
import java.util.List;
import com.pickong.common.domain.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdateFeedRequest {
    @NotBlank(message = "내용은 필수입니다.")
    private String content;
    @NotNull(message = "공개 범위는 필수입니다.")
    private Visibility visibility;
    // 프론트 작성 화면과 같이 최대 10장, null 항목은 받지 않음
    @Size(max = 10, message = "사진은 10장까지 올릴 수 있어요.")
    private List<@NotNull(message = "사진 id가 비어 있어요.") Long> mediaFileIds;
    private List<Long> placeIds;

    public UpdateFeedCommand toCommand() {
        return new UpdateFeedCommand(content, visibility, mediaFileIds, placeIds);
    }
}