package com.pickong.feed.adapter.in.web.request;

import com.pickong.feed.application.dto.CreateCommentCommand;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class CreateCommentRequest {
    // TODO(JWT): JWT 도입 후 이 필드 제거 — userId는 SecurityContext에서 꺼내 써야됨
    private Long userId;
    // 대댓글이면 부모 댓글 id, 최상위 댓글이면 null
    private Long parentId;
    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    public CreateCommentCommand toCommand() {
        return new CreateCommentCommand(userId, parentId, content);
    }
}
