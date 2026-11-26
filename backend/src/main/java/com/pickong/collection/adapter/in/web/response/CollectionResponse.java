package com.pickong.collection.adapter.in.web.response;

import com.pickong.collection.application.dto.CollectionSummary;
import com.pickong.common.domain.Visibility;
import com.pickong.user.application.dto.UserSummary;
import java.util.UUID;

/**
 * 컬렉션 카드. 프론트 CollectionDto와 같은 모양입니다.
 *
 * @param id          컬렉션 public_id
 * @param pinColor    지도 핀 색 (DB pin_style)
 * @param isPurchased 구매 테이블이 아직 없어 항상 false
 * @param isLocked    유료인데 내 것이 아니고 사지 않았으면 true (장소는 미리보기만)
 */
public record CollectionResponse(
        UUID id,
        Owner owner,
        String title,
        String description,
        String coverImageUrl,
        Visibility visibility,
        String pinColor,
        int placeCount,
        boolean isMine,
        Integer price,
        boolean isPurchased,
        boolean isLocked) {

    /**
     * 프론트 UserSummaryDto. 화면은 아이디만 보여 주지만, nickname은 프론트 모양을 맞추려고 이름(없으면 아이디)을 넣습니다.
     */
    public record Owner(UUID id, String username, String nickname, String profileImageUrl) {

        static Owner from(UserSummary user) {
            return new Owner(user.publicId(), user.username(),
                    user.name() != null ? user.name() : user.username(), user.profileImageUrl());
        }
    }

    public static CollectionResponse of(CollectionSummary c, UserSummary owner, boolean mine) {
        boolean purchased = false;
        return new CollectionResponse(c.publicId(), Owner.from(owner), c.title(), c.description(), c.coverImageUrl(),
                c.visibility(), c.pinStyle(), c.placeCount(), mine, c.price(), purchased,
                c.price() != null && !mine && !purchased);
    }
}
