package com.pickong.conversation.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;

/** POST /conversations/direct 요청 본문: {@code {"username":"jimin"}} — 대화할 상대의 아이디 (대소문자 무시) */
public record OpenDirectRequest(@NotBlank(message = "대화할 상대를 골라 주세요.") String username) {
}
