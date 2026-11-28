package com.pickong.conversation.adapter.in.web.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** POST /conversations/{id}/members 요청 본문: {@code {"usernames":["haeun"]}} — 그룹 멤버 누구나 초대할 수 있습니다. */
public record InviteRequest(@NotEmpty(message = "초대할 사람을 골라 주세요.") List<String> usernames) {
}
