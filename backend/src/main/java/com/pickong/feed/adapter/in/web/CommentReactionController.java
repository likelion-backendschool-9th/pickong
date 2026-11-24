package com.pickong.feed.adapter.in.web;

import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.feed.application.port.in.CommentReactionUseCase;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/feeds/{feedId}/comments/{commentId}/likes")
public class CommentReactionController {

    private final CommentReactionUseCase commentReactionUseCase;

    public CommentReactionController(CommentReactionUseCase commentReactionUseCase) {
        this.commentReactionUseCase = commentReactionUseCase;
    }

    // 댓글 좋아요 추가 — 성공 시 201 Created
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void likeComment(
            @PathVariable @Positive Long feedId,
            @PathVariable @Positive Long commentId,
            @CurrentUser LoginUser user) {
        commentReactionUseCase.likeComment(feedId, commentId, user.userId());
    }

    // 댓글 좋아요 취소 — 성공 시 204 No Content
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikeComment(
            @PathVariable @Positive Long feedId,
            @PathVariable @Positive Long commentId,
            @CurrentUser LoginUser user) {
        commentReactionUseCase.unlikeComment(feedId, commentId, user.userId());
    }
}
