package com.pickong.feed.application.service;

import com.pickong.feed.application.dto.CommentView;
import com.pickong.feed.application.dto.FeedAuthor;
import com.pickong.feed.application.port.in.CommentViewUseCase;
import com.pickong.feed.application.port.out.FeedViewQueryPort;
import com.pickong.feed.domain.model.Comment;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentViewService implements CommentViewUseCase {

    // 작성자 조회는 게시물 응답과 같은 SQL을 씀 (FeedViewQueryAdapter.findAuthors)
    private final FeedViewQueryPort feedViewQueryPort;

    public CommentViewService(FeedViewQueryPort feedViewQueryPort) {
        this.feedViewQueryPort = feedViewQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentView> viewsOf(List<Comment> comments) {
        if (comments.isEmpty()) {
            return List.of();
        }
        // 1. 작성자 id 모으기 → 2. 한 번에 조회 → 3. 댓글마다 붙이기
        Map<Long, FeedAuthor> authors = feedViewQueryPort.findAuthors(
                comments.stream().map(Comment::getUserId).distinct().toList());
        return comments.stream()
                .map(comment -> new CommentView(comment, authors.getOrDefault(comment.getUserId(), FeedAuthor.unknown())))
                .toList();
    }
}
