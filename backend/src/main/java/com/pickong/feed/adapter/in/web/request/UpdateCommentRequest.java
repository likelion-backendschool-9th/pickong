package com.pickong.feed.adapter.in.web.request;

import com.pickong.feed.application.dto.UpdateCommentCommand;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class UpdateCommentRequest {
    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    public UpdateCommentCommand toCommand() {
        return new UpdateCommentCommand(content);
    }
}
