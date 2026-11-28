package com.pickong.conversation.adapter.in.web.request;

import com.pickong.conversation.application.dto.UpdateConversationCommand;

/**
 * PATCH /conversations/{id} 요청 본문: {@code {"title":"새 이름"}} · {@code {"muted":true}}. 보낸 항목만 바꿉니다.
 * 이름은 그룹만(멤버 누구나), 알림 끄기는 내 설정입니다.
 */
public record UpdateConversationRequest(String title, Boolean muted) {

    public UpdateConversationCommand toCommand() {
        return new UpdateConversationCommand(title, muted);
    }
}
