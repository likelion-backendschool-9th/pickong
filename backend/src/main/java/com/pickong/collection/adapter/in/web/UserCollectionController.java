package com.pickong.collection.adapter.in.web;

import com.pickong.collection.adapter.in.web.response.CollectionResponse;
import com.pickong.collection.application.dto.UserCollectionsResult;
import com.pickong.collection.application.port.in.CollectionQueryUseCase;
import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로필의 컬렉션 탭.
 * <p>
 * 프로필 조회(GET /users/{username})처럼 로그인이 필요합니다. 공개로 열면 Access Token이 만료된 채 열었을 때
 * 401(→ 재발급) 없이 비로그인으로 처리되어, 내 프로필인데 비공개 컬렉션이 빠지고 isMine=false로 보이기 때문입니다.
 */
@RestController
public class UserCollectionController {

    private final CollectionQueryUseCase collectionQueryUseCase;

    public UserCollectionController(CollectionQueryUseCase collectionQueryUseCase) {
        this.collectionQueryUseCase = collectionQueryUseCase;
    }

    /** GET /api/v1/users/{username}/collections — 아이디는 대소문자를 무시합니다. 없거나 정상(ACTIVE) 회원이 아니면 404 USER_NOT_FOUND */
    @GetMapping("/users/{username}/collections")
    public List<CollectionResponse> collections(@CurrentUser LoginUser viewer, @PathVariable String username) {
        UserCollectionsResult result = collectionQueryUseCase.collectionsOf(viewer.userId(), username);
        return result.collections().stream()
                .map(c -> CollectionResponse.of(c, result.owner(), result.mine()))
                .toList();
    }
}
