package com.pickong.feed.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.ErrorCode;
import com.pickong.feed.application.dto.CommentPage;
import com.pickong.feed.application.dto.CreateCommentCommand;
import com.pickong.feed.application.dto.UpdateCommentCommand;
import com.pickong.feed.application.port.out.CommentRepository;
import com.pickong.feed.domain.model.Comment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

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

    @Test
    void createComment_성공() {
        // given
        CreateCommentCommand command = new CreateCommentCommand(1L, null, "테스트 댓글");
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
        // given: 댓글 2개 반환 (hasNext = false)
        given(commentRepository.findByFeedId(10L, null, 11))
                .willReturn(List.of(sampleComment(), sampleComment()));

        // when
        CommentPage page = commentService.getComments(10L, null, 10);

        // then
        assertThat(page.getComments()).hasSize(2);
        assertThat(page.isHasNext()).isFalse();
    }

    @Test
    void updateComment_존재하지않는id_예외발생() {
        // given
        given(commentRepository.findById(999L))
                .willThrow(new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> commentService.updateComment(999L,
                new UpdateCommentCommand("수정 내용")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("댓글을 찾을 수 없습니다");
    }

    @Test
    void deleteComment_존재하지않는id_예외발생() {
        // given
        willThrow(new BusinessException(ErrorCode.COMMENT_NOT_FOUND))
                .given(commentRepository).deleteById(999L);

        // when & then
        assertThatThrownBy(() -> commentService.deleteComment(999L))
                .isInstanceOf(BusinessException.class);
    }
}
