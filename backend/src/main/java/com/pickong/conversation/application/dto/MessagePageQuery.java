package com.pickong.conversation.application.dto;

/**
 * 메시지 내역 조회 조건. {@code cursor}와 {@code after}는 함께 쓰지 않습니다.
 *
 * @param cursor 이 id보다 오래된 메시지 (이전 내역). 둘 다 null이면 최신 메시지부터
 * @param after  이 id보다 새 메시지 (끊긴 동안 빠진 메시지 보충)
 * @param size   한 번에 가져올 최대 개수
 */
public record MessagePageQuery(Long cursor, Long after, int size) {
}
