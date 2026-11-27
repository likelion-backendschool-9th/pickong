package com.pickong.collection.application.dto;

import com.pickong.common.domain.Visibility;
import java.util.UUID;

/**
 * 컬렉션 목록의 한 건 (카드에 필요한 값만).
 *
 * @param coverImageUrl 표지. 따로 정하지 않았으면 보는 사람이 볼 수 있는 첫 장소의 사진, 그것도 없으면 null
 * @param pinStyle      지도 핀 색 (LIME · GREEN · BROWN)
 * @param price         가격(원). null이면 무료
 */
public record CollectionSummary(
        UUID publicId,
        String title,
        String description,
        String coverImageUrl,
        Visibility visibility,
        String pinStyle,
        int placeCount,
        Integer price) {
}
