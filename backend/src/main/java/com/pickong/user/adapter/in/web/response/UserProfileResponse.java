package com.pickong.user.adapter.in.web.response;

import com.pickong.user.application.dto.UserProfile;

/**
 * GET /users/{username} 응답. 프론트 {@code entities/user/api/dto.ts}의 UserDto 모양입니다.
 * 내 정보(MeResponse)와 달리 {@code usernameChangeableAt}은 보내지 않습니다.
 *
 * @param id          users.public_id (순번 id는 내보내지 않음)
 * @param nickname    화면에 보여 줄 이름 = 이름 ?? 아이디. 서버가 계산해서 보냅니다
 * @param name        사용자가 정한 이름. 없으면 null
 * @param postCount   게시물 수 (users.feed_count)
 * @param isFollowing 팔로우 테이블(follows)이 생기기 전까지 false
 * @param isMe        보는 사람 자신의 프로필인지
 * @param isCreator   크리에이터 기능이 생기기 전까지 false
 */
public record UserProfileResponse(
        String id,
        String username,
        String nickname,
        String name,
        String profileImageUrl,
        String bio,
        String region,
        long postCount,
        long followerCount,
        long followingCount,
        boolean isFollowing,
        boolean isMe,
        boolean isCreator) {

    public static UserProfileResponse from(UserProfile profile) {
        return new UserProfileResponse(profile.publicId().toString(), profile.username(),
                profile.name() != null ? profile.name() : profile.username(), profile.name(), profile.profileImageUrl(),
                profile.bio(), profile.region(), profile.feedCount(), profile.followerCount(), profile.followingCount(),
                false, profile.me(), false);
    }
}
