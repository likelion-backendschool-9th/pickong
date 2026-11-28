package com.pickong.conversation.adapter.in.web.response;

import com.pickong.conversation.application.dto.MessagePageResult;
import java.util.List;

/**
 * 메시지 내역 커서 페이지 (common 5.6). {@code items}는 오래된 순입니다.
 *
 * @param nextCursor 더 있으면 다음 요청의 {@code cursor}(이전 내역) 또는 {@code after}(새 메시지) 값, 없으면 null
 */
public record MessagePageResponse(List<MessageResponse> items, String nextCursor) {

    public static MessagePageResponse from(MessagePageResult page) {
        return new MessagePageResponse(page.items().stream().map(MessageResponse::from).toList(),
                page.nextCursor() == null ? null : String.valueOf(page.nextCursor()));
    }
}
