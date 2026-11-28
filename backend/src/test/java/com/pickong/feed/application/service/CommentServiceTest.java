package com.pickong.feed.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.application.dto.CommentPage;
import com.pickong.feed.application.dto.CreateCommentCommand;
import com.pickong.feed.application.dto.UpdateCommentCommand;
import com.pickong.feed.application.port.out.CommentRepository;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Comment;
import com.pickong.feed.domain.model.Feed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.pickong.common.domain.Visibility;
import com.pickong.social.application.port.in.RelationQueryUseCase;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private FeedRepository feedRepository;

    // 관계를 정해 주지 않으면 null → 도메인이 ANONYMOUS로 판정 (PUBLIC 게시물만 통과)
    @Mock
    private RelationQueryUseCase relationQueryUseCase;

    @InjectMocks
    private CommentService commentService;

    private Comment sampleComment() {
        return Comment.builder()
                .id(1L)
                .feedId(10L)
                .userId(1L)
                .content("테스트 댓글")
                .likeCount(0)
                .build();
    }

    private Feed sampleFeed() {
        return Feed.builder().id(10L).userId(1L).content("피드").visibility(Visibility.PUBLIC).build();
    }

    @Test
    void createComment_성공() {
        // given
        CreateCommentCommand command = new CreateCommentCommand(1L, null, "테스트 댓글");
        given(feedRepository.findByIdForUpdate(10L)).willReturn(sampleFeed());
        given(commentRepository.save(any(Comment.class))).willReturn(sampleComment());

        // when
        Comment result = commentService.createComment(10L, command);

        // then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getContent()).isEqualTo("테스트 댓글");
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void getComments_성공() {
        given(feedRepository.findById(10L)).willReturn(sampleFeed());
        // given: 댓글 2개 반환 (hasNext = false)
        given(commentRepository.findByFeedId(10L, null, 11, null, false))
                .willReturn(List.of(sampleComment(), sampleComment()));

        // when
        CommentPage page = commentService.getComments(10L, null, 10);

        // then
        assertThat(page.getComments()).hasSize(2);
        assertThat(page.isHasNext()).isFalse();
    }

    @Test
    void updateComment_존재하지않는id_예외발생() {
        given(feedRepository.findByIdForUpdate(10L)).willReturn(sampleFeed());
        // given
        given(commentRepository.findByIdForUpdate(999L))
                .willThrow(new BusinessException(FeedErrorCode.COMMENT_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> commentService.updateComment(10L, 999L, 1L,
                new UpdateCommentCommand("수정 내용")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("댓글을 찾을 수 없습니다");
    }

    @Test
    void updateComment_다른피드댓글_수정시_예외발생() {
        given(feedRepository.findByIdForUpdate(10L)).willReturn(sampleFeed());
        // given: feedId=10인데 댓글은 feedId=99에 속함
        Comment wrongFeedComment = Comment.builder()
                .id(1L)
                .feedId(99L)
                .userId(1L)
                .content("다른 피드 댓글")
                .likeCount(0)
                .build();
        given(commentRepository.findByIdForUpdate(1L)).willReturn(wrongFeedComment);

        // when & then
        assertThatThrownBy(() -> commentService.updateComment(10L, 1L, 1L,
                new UpdateCommentCommand("수정 내용")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("댓글이 해당 피드에 속하지 않습니다");
    }

    @Test
    void updateComment_타인댓글_수정시_예외발생() {
        given(feedRepository.findByIdForUpdate(10L)).willReturn(sampleFeed());
        // given: 댓글 작성자는 userId=2, 요청자는 userId=99
        Comment otherUsersComment = Comment.builder()
                .id(1L)
                .feedId(10L)
                .userId(2L)
                .content("타인의 댓글")
                .likeCount(0)
                .build();
        given(commentRepository.findByIdForUpdate(1L)).willReturn(otherUsersComment);

        // when & then
        assertThatThrownBy(() -> commentService.updateComment(10L, 1L, 99L,
                new UpdateCommentCommand("수정 내용")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("댓글 작성자만 수정·삭제할 수 있습니다");
    }

    // 상태만 바꾼 댓글 (작성자, 삭제 시각, 숨김 시각)
    private Comment commentWithState(long authorId, OffsetDateTime deletedAt, OffsetDateTime hiddenAt) {
        return Comment.builder()
                .id(1L)
                .feedId(10L)
                .userId(authorId)
                .content("상태 댓글")
                .likeCount(0)
                .deletedAt(deletedAt)
                .hiddenAt(hiddenAt)
                .build();
    }

    @Test
    void updateComment_삭제된_내댓글은_404이고_저장하지_않음() {
        given(feedRepository.findByIdForUpdate(10L)).willReturn(sampleFeed());
        given(commentRepository.findByIdForUpdate(1L)).willReturn(commentWithState(1L, OffsetDateTime.now(), null));

        assertThatThrownBy(() -> commentService.updateComment(10L, 1L, 1L, new UpdateCommentCommand("수정 내용")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(FeedErrorCode.COMMENT_NOT_FOUND);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void updateComment_남의_삭제된댓글은_403이_아니라_404() {
        // 삭제 확인이 본인 확인보다 먼저라 COMMENT_NOT_OWNER로 댓글이 있었다는 사실이 드러나지 않음
        given(feedRepository.findByIdForUpdate(10L)).willReturn(sampleFeed());
        given(commentRepository.findByIdForUpdate(1L)).willReturn(commentWithState(2L, OffsetDateTime.now(), null));

        assertThatThrownBy(() -> commentService.updateComment(10L, 1L, 99L, new UpdateCommentCommand("수정 내용")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(FeedErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    void updateComment_남의_숨긴댓글은_403() {
        // 숨김 댓글이어도 본인이 아니면 지금처럼 COMMENT_NOT_OWNER
        given(feedRepository.findByIdForUpdate(10L)).willReturn(sampleFeed());
        given(commentRepository.findByIdForUpdate(1L)).willReturn(commentWithState(2L, null, OffsetDateTime.now()));

        assertThatThrownBy(() -> commentService.updateComment(10L, 1L, 99L, new UpdateCommentCommand("수정 내용")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(FeedErrorCode.COMMENT_NOT_OWNER);
    }

    @Test
    void deleteComment_이미_삭제된댓글은_404이고_다시_지우지_않음() {
        given(feedRepository.findByIdForUpdate(10L)).willReturn(sampleFeed());
        given(commentRepository.findByIdForUpdate(1L)).willReturn(commentWithState(1L, OffsetDateTime.now(), null));

        assertThatThrownBy(() -> commentService.deleteComment(10L, 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(FeedErrorCode.COMMENT_NOT_FOUND);
        verify(commentRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteComment_관리자가_숨긴_내댓글은_삭제_허용() {
        given(feedRepository.findByIdForUpdate(10L)).willReturn(sampleFeed());
        given(commentRepository.findByIdForUpdate(1L)).willReturn(commentWithState(1L, null, OffsetDateTime.now()));

        commentService.deleteComment(10L, 1L, 1L);

        verify(commentRepository).deleteById(1L);
    }

    @Test
    void deleteComment_존재하지않는id_예외발생() {
        given(feedRepository.findByIdForUpdate(10L)).willReturn(sampleFeed());
        // given
        given(commentRepository.findByIdForUpdate(999L))
                .willThrow(new BusinessException(FeedErrorCode.COMMENT_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> commentService.deleteComment(10L, 999L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteComment_다른피드댓글_삭제시_예외발생() {
        given(feedRepository.findByIdForUpdate(10L)).willReturn(sampleFeed());
        // given: feedId=10인데 댓글은 feedId=99에 속함
        Comment wrongFeedComment = Comment.builder()
                .id(1L)
                .feedId(99L)
                .userId(1L)
                .content("다른 피드 댓글")
                .likeCount(0)
                .build();
        given(commentRepository.findByIdForUpdate(1L)).willReturn(wrongFeedComment);

        // when & then
        assertThatThrownBy(() -> commentService.deleteComment(10L, 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("댓글이 해당 피드에 속하지 않습니다");
    }

    @Test
    void deleteComment_타인댓글_삭제시_예외발생() {
        given(feedRepository.findByIdForUpdate(10L)).willReturn(sampleFeed());
        // given: 댓글 작성자는 userId=2, 요청자는 userId=99
        Comment otherUsersComment = Comment.builder()
                .id(1L)
                .feedId(10L)
                .userId(2L)
                .content("타인의 댓글")
                .likeCount(0)
                .build();
        given(commentRepository.findByIdForUpdate(1L)).willReturn(otherUsersComment);

        // when & then
        assertThatThrownBy(() -> commentService.deleteComment(10L, 1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("댓글 작성자만 수정·삭제할 수 있습니다");
    }

    @Test
    void createComment_존재하지않는_feedId_예외발생() {
        // given: 없는 feedId → FEED_NOT_FOUND
        given(feedRepository.findByIdForUpdate(999L))
                .willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND));

        CreateCommentCommand command = new CreateCommentCommand(1L, null, "댓글");

        // when & then
        assertThatThrownBy(() -> commentService.createComment(999L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("피드를 찾을 수 없습니다");
    }

    @Test
    void createComment_대댓글에_대댓글_작성시_예외발생() {
        // given: 부모 댓글이 이미 대댓글인 상황 (parentId가 있음)
        Comment replyComment = Comment.builder()
                .id(2L)
                .feedId(10L)
                .userId(1L)
                .parentId(1L) // 이미 대댓글
                .content("대댓글")
                .likeCount(0)
                .build();
        given(feedRepository.findByIdForUpdate(10L)).willReturn(sampleFeed());
        given(commentRepository.findById(2L)).willReturn(replyComment);

        CreateCommentCommand command = new CreateCommentCommand(1L, 2L, "대댓글의 대댓글");

        // when & then
        assertThatThrownBy(() -> commentService.createComment(10L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("대댓글에는 답글을 달 수 없습니다");
    }

    @Test
    void createComment_최상위댓글에_대댓글_작성_성공() {
        // given: 부모 댓글이 최상위 댓글 (parentId 없음)
        Comment topLevelComment = Comment.builder()
                .id(1L)
                .feedId(10L)
                .userId(1L)
                .content("최상위 댓글")
                .likeCount(0)
                .build();
        Comment savedReply = Comment.builder()
                .id(3L)
                .feedId(10L)
                .userId(2L)
                .parentId(1L)
                .content("대댓글")
                .likeCount(0)
                .build();
        given(feedRepository.findByIdForUpdate(10L)).willReturn(sampleFeed());
        given(commentRepository.findById(1L)).willReturn(topLevelComment);
        given(commentRepository.save(any(Comment.class))).willReturn(savedReply);

        CreateCommentCommand command = new CreateCommentCommand(2L, 1L, "대댓글");

        // when
        Comment result = commentService.createComment(10L, command);

        // then
        assertThat(result.getParentId()).isEqualTo(1L);
        assertThat(result.isReply()).isTrue();
    }
}
