package com.pickong.user.application.dto;

import java.util.UUID;

/**
 * 프로필 (GET /users/{username} 결과). 내 정보(MyProfile)와 달리 아이디 변경 가능 시각은 없습니다.
 *
 * @param publicId  밖에 보여 주는 회원 id (users.public_id)
 * @param name      사용자가 정한 이름. 없으면 null
 * @param feedCount 게시물 수 (users.feed_count, 피드 기능이 갱신)
 * @param me        보는 사람 자신의 프로필인지
 */
public record UserProfile(
        UUID publicId,
        String username,
        String name,
        String profileImageUrl,
        String bio,
        String region,
        long feedCount,
        long followerCount,
        long followingCount,
        boolean me) {
}
