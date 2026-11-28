package com.pickong.conversation.adapter.in.web.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * POST /conversations/group 요청 본문: {@code {"title":"성수 카페 투어","usernames":["jimin","minsu"]}}.
 * 나를 포함해 3~10명이고, 이름은 1~20자입니다 (규칙 확인은 서비스 · domain).
 */
public record CreateGroupRequest(String title, @NotNull(message = "초대할 사람을 골라 주세요.") List<String> usernames) {
}
