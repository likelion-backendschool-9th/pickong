package com.pickong.user.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 내 정보 (GET · PATCH /me 결과).
 *
 * @param publicId             밖에 보여 주는 회원 id (users.public_id)
 * @param name                 사용자가 정한 이름. 없으면 null
 * @param feedCount            게시물 수 (users.feed_count, 피드 기능이 갱신)
 * @param usernameChangeableAt 아이디를 다시 바꿀 수 있는 시각. 지금 바로 바꿀 수 있으면 null
 */
public record MyProfile(
        UUID publicId,
        String username,
        String name,
        String profileImageUrl,
        String bio,
        String region,
        long feedCount,
        long followerCount,
        long followingCount,
        OffsetDateTime usernameChangeableAt) {
}
