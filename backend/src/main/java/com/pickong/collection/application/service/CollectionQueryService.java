package com.pickong.collection.application.service;

import com.pickong.collection.application.dto.UserCollectionsResult;
import com.pickong.collection.application.port.in.CollectionQueryUseCase;
import com.pickong.collection.application.port.out.CollectionQueryPort;
import com.pickong.common.domain.ViewerRelation;
import com.pickong.common.domain.Visibility;
import com.pickong.user.application.dto.UserSummary;
import com.pickong.user.application.port.in.UserQueryUseCase;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 컬렉션 조회. 어떤 공개 범위를 보여 줄지는 Visibility.allows(관계)가 정합니다. */
@Service
@Transactional(readOnly = true)
public class CollectionQueryService implements CollectionQueryUseCase {

    private final UserQueryUseCase userQueryUseCase;
    private final CollectionQueryPort collectionQueryPort;

    public CollectionQueryService(UserQueryUseCase userQueryUseCase, CollectionQueryPort collectionQueryPort) {
        this.userQueryUseCase = userQueryUseCase;
        this.collectionQueryPort = collectionQueryPort;
    }

    @Override
    public UserCollectionsResult collectionsOf(Long viewerId, String username) {
        UserSummary owner = userQueryUseCase.getActiveByUsername(username);
        boolean mine = owner.id().equals(viewerId);
        // 팔로우 테이블이 아직 없어 FOLLOWER는 판단하지 않습니다. 그동안 팔로워 공개 컬렉션은 본인에게만 보입니다.
        ViewerRelation relation = mine ? ViewerRelation.OWNER : ViewerRelation.OTHER;
        Set<Visibility> visible = Arrays.stream(Visibility.values())
                .filter(v -> v.allows(relation))
                .collect(Collectors.toUnmodifiableSet());
        return new UserCollectionsResult(owner, mine, collectionQueryPort.findByOwner(owner.id(), visible, viewerId));
    }
}
