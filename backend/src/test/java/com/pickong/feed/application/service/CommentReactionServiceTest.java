package com.pickong.feed.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.application.port.out.CommentReactionRepository;
import com.pickong.feed.application.port.out.CommentRepository;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Comment;
import com.pickong.feed.domain.model.Feed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentReactionServiceTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentReactionRepository commentReactionRepository;

    @InjectMocks
    private CommentReactionService commentReactionService;

    private Feed sampleFeed() {
        return Feed.builder().id(10L).userId(1L).content("피드").build();
    }

    private Comment sampleComment() {
        return Comment.builder()
                .id(1L)
                .feedId(10L)
                .userId(1L)
                .content("테스트 댓글")
                .likeCount(0)
                .build();
    }

    // ── likeComment ────────────────────────────────────────────

    @Test
    void likeComment_성공() {
        given(feedRepository.findById(10L)).willReturn(sampleFeed());
        given(commentRepository.findById(1L)).willReturn(sampleComment());
        given(commentReactionRepository.existsCommentLike(1L, 2L)).willReturn(false);

        commentReactionService.likeComment(10L, 1L, 2L);

        verify(commentReactionRepository).saveCommentLike(1L, 2L);
    }

    @Test
    void likeComment_존재하지않는피드_예외발생() {
        given(feedRepository.findById(anyLong()))
                .willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND));

        assertThatThrownBy(() -> commentReactionService.likeComment(999L, 1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("피드를 찾을 수 없습니다");
    }

    @Test
    void likeComment_존재하지않는댓글_예외발생() {
        given(feedRepository.findById(10L)).willReturn(sampleFeed());
        given(commentRepository.findById(anyLong()))
                .willThrow(new BusinessException(FeedErrorCode.COMMENT_NOT_FOUND));

        assertThatThrownBy(() -> commentReactionService.likeComment(10L, 999L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("댓글을 찾을 수 없습니다");
    }

    @Test
    void likeComment_다른피드댓글_예외발생() {
        // 댓글의 feedId(99L)가 경로의 feedId(10L)와 다른 케이스
        Comment wrongFeedComment = Comment.builder()
                .id(1L).feedId(99L).userId(1L).content("다른 피드 댓글").likeCount(0).build();
        given(feedRepository.findById(10L)).willReturn(sampleFeed());
        given(commentRepository.findById(1L)).willReturn(wrongFeedComment);

        assertThatThrownBy(() -> commentReactionService.likeComment(10L, 1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("댓글이 해당 피드에 속하지 않습니다");
    }

    @Test
    void likeComment_이미좋아요_예외발생() {
        given(feedRepository.findById(10L)).willReturn(sampleFeed());
        given(commentRepository.findById(1L)).willReturn(sampleComment());
        given(commentReactionRepository.existsCommentLike(1L, 2L)).willReturn(true);

        assertThatThrownBy(() -> commentReactionService.likeComment(10L, 1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 좋아요한 댓글입니다");
    }

    // ── unlikeComment ──────────────────────────────────────────

    @Test
    void unlikeComment_성공() {
        given(feedRepository.findById(10L)).willReturn(sampleFeed());
        given(commentRepository.findById(1L)).willReturn(sampleComment());
        given(commentReactionRepository.deleteCommentLike(1L, 2L)).willReturn(true);

        commentReactionService.unlikeComment(10L, 1L, 2L);

        verify(commentReactionRepository).deleteCommentLike(1L, 2L);
    }

    @Test
    void unlikeComment_존재하지않는피드_예외발생() {
        given(feedRepository.findById(anyLong()))
                .willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND));

        assertThatThrownBy(() -> commentReactionService.unlikeComment(999L, 1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("피드를 찾을 수 없습니다");
    }

    @Test
    void unlikeComment_좋아요없음_예외발생() {
        given(feedRepository.findById(10L)).willReturn(sampleFeed());
        given(commentRepository.findById(1L)).willReturn(sampleComment());
        given(commentReactionRepository.deleteCommentLike(1L, 2L)).willReturn(false);

        assertThatThrownBy(() -> commentReactionService.unlikeComment(10L, 1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("댓글 좋아요를 찾을 수 없습니다");
    }
}
