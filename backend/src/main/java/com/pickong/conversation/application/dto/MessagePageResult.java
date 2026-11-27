package com.pickong.conversation.application.dto;

import java.util.List;

/**
 * 메시지 내역 한 페이지. {@code items}는 항상 오래된 순(id 오름차순)입니다.
 *
 * @param nextCursor 더 있으면 다음 요청 값, 없으면 null.
 *                   이전 내역(cursor)을 볼 때는 다음 {@code cursor}(이 페이지에서 가장 오래된 id),
 *                   새 메시지(after)를 볼 때는 다음 {@code after}(이 페이지에서 가장 새 id)
 */
public record MessagePageResult(List<MessageResult> items, Long nextCursor) {
}
