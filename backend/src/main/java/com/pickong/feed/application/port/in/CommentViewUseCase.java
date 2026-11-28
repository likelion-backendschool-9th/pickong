package com.pickong.feed.application.port.in;

import com.pickong.feed.application.dto.CommentView;
import com.pickong.feed.domain.model.Comment;
import java.util.List;

/**
 * 조회한 댓글에 작성자 요약을 붙임
 * 공개 범위 · 숨김 검사는 하지 않음. 이미 CommentUseCase가 볼 수 있다고 판단한 결과만 넘겨야 됨
 */
public interface CommentViewUseCase {

    // 넘긴 순서 그대로 돌려줌. 작성자를 한 번에 조회해서 목록에서 댓글마다 쿼리가 나가지 않게 함
    List<CommentView> viewsOf(List<Comment> comments);

    default CommentView viewOf(Comment comment) {
        return viewsOf(List.of(comment)).get(0);
    }
}
