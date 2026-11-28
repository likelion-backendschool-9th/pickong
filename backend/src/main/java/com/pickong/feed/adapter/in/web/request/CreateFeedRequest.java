package com.pickong.feed.adapter.in.web.request;

import com.pickong.common.domain.Visibility;
import java.util.List;
import com.pickong.feed.application.dto.CreateFeedCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CreateFeedRequest {
    @NotBlank(message = "내용은 필수입니다.")
    private String content;
    @NotNull(message = "공개 범위는 필수입니다.")
    private Visibility visibility;
    // 프론트 작성 화면과 같이 최대 10장, null 항목은 받지 않음
    @Size(max = 10, message = "사진은 10장까지 올릴 수 있어요.")
    private List<@NotNull(message = "사진 id가 비어 있어요.") Long> mediaFileIds;
    private List<Long> placeIds;

    // userId는 요청 본문에서 받지 않고 SecurityContext의 인증된 사용자로 채움
    public CreateFeedCommand toCommand(Long userId) {
        return new CreateFeedCommand(userId, content, visibility, mediaFileIds, placeIds);
    }
}
