package com.pickong.feed.adapter.in.web.request;

import com.pickong.feed.application.dto.CreateCommentCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CreateCommentRequest {
    // 대댓글이면 부모 댓글 id, 최상위 댓글이면 null
    private Long parentId;
    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 1000, message = "댓글은 1000자 이하이어야 합니다.")
    private String content;

    public CreateCommentCommand toCommand(Long userId) {
        return new CreateCommentCommand(userId, parentId, content);
    }
}
