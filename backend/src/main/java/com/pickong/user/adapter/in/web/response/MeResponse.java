package com.pickong.user.adapter.in.web.response;

import com.pickong.user.application.dto.MyProfile;
import java.time.OffsetDateTime;

/**
 * GET · PATCH /me 응답. 프론트 {@code entities/user/api/dto.ts}의 UserDto 모양에 {@code usernameChangeableAt}을 더했습니다.
 *
 * @param id                   users.public_id (순번 id는 내보내지 않음)
 * @param nickname             화면에 보여 줄 이름 = 이름 ?? 아이디. 서버가 계산해서 보냅니다
 * @param name                 사용자가 정한 이름. 없으면 null
 * @param postCount            게시물 수. 피드 기능이 users.feed_count를 갱신하기 전까지 0
 * @param isFollowing          나 자신이라 항상 false
 * @param isMe                 항상 true
 * @param isCreator            크리에이터 기능이 생기기 전까지 false
 * @param usernameChangeableAt 아이디를 다시 바꿀 수 있는 시각. 지금 바꿀 수 있으면 null
 */
public record MeResponse(
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
        boolean isCreator,
        OffsetDateTime usernameChangeableAt) {

    public static MeResponse from(MyProfile me) {
        return new MeResponse(me.publicId().toString(), me.username(), me.name() != null ? me.name() : me.username(),
                me.name(), me.profileImageUrl(), me.bio(), me.region(), me.feedCount(), me.followerCount(),
                me.followingCount(), false, true, false, me.usernameChangeableAt());
    }
}
