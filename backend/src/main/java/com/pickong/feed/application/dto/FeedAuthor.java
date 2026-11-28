package com.pickong.feed.application.dto;

/**
 * 게시물 작성자 요약 (화면에 아이디, 프로필 사진, 지역만 씀)
 *
 * @param publicId users.public_id (순번 id는 내보내지 않음)
 * @param name     사용자가 정한 이름. 없으면 null
 */
public record FeedAuthor(String publicId, String username, String name, String profileImageUrl, String region) {

    // 작성자 행을 찾지 못했을 때 화면이 깨지지 않게 쓰는 값 (users 행은 익명화돼도 지워지지 않아 보통 생기지 않음)
    public static FeedAuthor unknown() {
        return new FeedAuthor(null, "unknown", null, null, null);
    }

    // 화면에 보여 줄 이름 = 이름 ?? 아이디 (프로필 응답과 같은 규칙)
    public String nickname() {
        return name != null ? name : username;
    }
}
