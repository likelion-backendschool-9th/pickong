package com.pickong.feed.adapter.in.web.request;

import com.pickong.feed.application.dto.UpdateCommentCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdateCommentRequest {
    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 1000, message = "댓글은 1000자 이하이어야 합니다.")
    private String content;

    public UpdateCommentCommand toCommand() {
        return new UpdateCommentCommand(content);
    }
}
