package com.pickong.social.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.social.application.port.in.RelationCommandUseCase;
import com.pickong.social.application.port.in.RelationQueryUseCase;
import com.pickong.social.application.port.out.RelationRepository;
import com.pickong.social.domain.SocialErrorCode;
import com.pickong.user.application.port.in.UserCounterUseCase;
import com.pickong.user.application.port.out.UserPersistencePort;
import com.pickong.user.domain.UserErrorCode;
import com.pickong.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RelationCommandService implements RelationCommandUseCase, RelationQueryUseCase {

    private final RelationRepository relationRepository;
    private final UserPersistencePort userPersistencePort;
    private final UserCounterUseCase userCounterUseCase;

    public RelationCommandService(RelationRepository relationRepository,
                                   UserPersistencePort userPersistencePort,
                                   UserCounterUseCase userCounterUseCase) {
        this.relationRepository = relationRepository;
        this.userPersistencePort = userPersistencePort;
        this.userCounterUseCase = userCounterUseCase;
    }

    @Override
    @Transactional
    public void follow(Long followerId, String targetUsername) {
        User target = userPersistencePort.findByUsername(targetUsername)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (followerId.equals(target.getId())) {
            throw new BusinessException(SocialErrorCode.CANNOT_FOLLOW_SELF);
        }

        if (relationRepository.existsFollow(followerId, target.getId())) {
            throw new BusinessException(SocialErrorCode.ALREADY_FOLLOWING);
        }

        relationRepository.saveFollow(followerId, target.getId());
        userCounterUseCase.incrementFollowingCount(followerId);
        userCounterUseCase.incrementFollowerCount(target.getId());
    }

    @Override
    @Transactional
    public void unfollow(Long followerId, String targetUsername) {
        User target = userPersistencePort.findByUsername(targetUsername)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        boolean deleted = relationRepository.deleteFollow(followerId, target.getId());
        if (!deleted) {
            throw new BusinessException(SocialErrorCode.FOLLOW_NOT_FOUND);
        }

        userCounterUseCase.decrementFollowingCount(followerId);
        userCounterUseCase.decrementFollowerCount(target.getId());
    }

    @Override
    public List<Long> getFollowingIds(Long userId) {
        return relationRepository.findFollowingIds(userId);
    }
}
