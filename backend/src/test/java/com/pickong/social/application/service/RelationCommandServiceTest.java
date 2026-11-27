package com.pickong.social.application.service;

import com.pickong.common.domain.ViewerRelation;
import com.pickong.common.error.BusinessException;
import com.pickong.social.application.port.out.RelationRepository;
import com.pickong.social.domain.SocialErrorCode;
import com.pickong.user.application.port.in.UserCounterUseCase;
import com.pickong.user.application.port.out.UserPersistencePort;
import com.pickong.user.domain.UserErrorCode;
import com.pickong.user.domain.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RelationCommandServiceTest {

    @Mock private RelationRepository relationRepository;
    @Mock private UserPersistencePort userPersistencePort;
    @Mock private UserCounterUseCase userCounterUseCase;

    @InjectMocks
    private RelationCommandService relationCommandService;

    private User targetUser() {
        return User.builder().id(2L).username("targetUser").build();
    }

    // ── follow ─────────────────────────────────────────────────

    @Test
    void follow_성공() {
        given(userPersistencePort.findByUsername("targetUser")).willReturn(Optional.of(targetUser()));
        given(relationRepository.existsFollow(1L, 2L)).willReturn(false);

        relationCommandService.follow(1L, "targetUser");

        verify(relationRepository).saveFollow(1L, 2L);
        verify(userCounterUseCase).incrementFollowingCount(1L);
        verify(userCounterUseCase).incrementFollowerCount(2L);
    }

    @Test
    void follow_존재하지않는사용자_예외발생() {
        given(userPersistencePort.findByUsername("nobody")).willReturn(Optional.empty());

        assertThatThrownBy(() -> relationCommandService.follow(1L, "nobody"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    void follow_자기자신팔로우_예외발생() {
        User self = User.builder().id(1L).username("me").build();
        given(userPersistencePort.findByUsername("me")).willReturn(Optional.of(self));

        assertThatThrownBy(() -> relationCommandService.follow(1L, "me"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SocialErrorCode.CANNOT_FOLLOW_SELF.getMessage());
    }

    @Test
    void follow_이미팔로우중_예외발생() {
        given(userPersistencePort.findByUsername("targetUser")).willReturn(Optional.of(targetUser()));
        given(relationRepository.existsFollow(1L, 2L)).willReturn(true);

        assertThatThrownBy(() -> relationCommandService.follow(1L, "targetUser"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SocialErrorCode.ALREADY_FOLLOWING.getMessage());
    }

    // ── unfollow ───────────────────────────────────────────────

    @Test
    void unfollow_성공() {
        given(userPersistencePort.findByUsername("targetUser")).willReturn(Optional.of(targetUser()));
        given(relationRepository.deleteFollow(1L, 2L)).willReturn(true);

        relationCommandService.unfollow(1L, "targetUser");

        verify(userCounterUseCase).decrementFollowingCount(1L);
        verify(userCounterUseCase).decrementFollowerCount(2L);
    }

    @Test
    void unfollow_존재하지않는사용자_예외발생() {
        given(userPersistencePort.findByUsername("nobody")).willReturn(Optional.empty());

        assertThatThrownBy(() -> relationCommandService.unfollow(1L, "nobody"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    void unfollow_팔로우관계없음_예외발생() {
        given(userPersistencePort.findByUsername("targetUser")).willReturn(Optional.of(targetUser()));
        given(relationRepository.deleteFollow(1L, 2L)).willReturn(false);

        assertThatThrownBy(() -> relationCommandService.unfollow(1L, "targetUser"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SocialErrorCode.FOLLOW_NOT_FOUND.getMessage());
    }

    // ── relationOf: 공개 범위 판정에 쓰는 관계 ─────────────────

    @Test
    void relationOf_비로그인이면_ANONYMOUS_팔로우조회안함() {
        assertThat(relationCommandService.relationOf(null, 2L)).isEqualTo(ViewerRelation.ANONYMOUS);
        verifyNoInteractions(relationRepository);
    }

    @Test
    void relationOf_본인이면_OWNER_팔로우조회안함() {
        assertThat(relationCommandService.relationOf(2L, 2L)).isEqualTo(ViewerRelation.OWNER);
        verifyNoInteractions(relationRepository);
    }

    @Test
    void relationOf_소유자를팔로우하면_FOLLOWER() {
        given(relationRepository.existsFollow(1L, 2L)).willReturn(true);

        assertThat(relationCommandService.relationOf(1L, 2L)).isEqualTo(ViewerRelation.FOLLOWER);
    }

    @Test
    void relationOf_팔로우하지않으면_OTHER() {
        given(relationRepository.existsFollow(1L, 2L)).willReturn(false);

        assertThat(relationCommandService.relationOf(1L, 2L)).isEqualTo(ViewerRelation.OTHER);
    }
}
