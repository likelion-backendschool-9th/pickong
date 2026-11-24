package com.pickong.moderation.application.dto;

/**
 * 관리자 화면에 필요한 회원 표시 정보.
 * id는 내부 BIGINT입니다. 이메일 등 화면 밖 정보는 담지 않습니다.
 */
public record ReportPartyView(long id, String username, String nickname, String profileImageUrl) {
}
