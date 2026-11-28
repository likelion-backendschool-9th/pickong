package com.pickong.conversation.domain;

import com.pickong.common.error.ErrorCode;
import lombok.Getter;

/** 대화(conversation) 모듈 오류 코드. */
@Getter
public enum ConversationErrorCode implements ErrorCode {

    /** 없는 대화방이거나, 참여하지 않았거나 나간 대화방 (참여하지 않은 대화방은 있다는 것도 알리지 않음) */
    CONVERSATION_NOT_FOUND(404, "대화를 찾을 수 없어요."),
    /** 나와의 1:1 대화방을 열려고 함 */
    SELF_CONVERSATION_NOT_ALLOWED(400, "나에게는 메시지를 보낼 수 없어요."),
    /** 읽음 위치로 보낸 메시지가 이 대화방에 없거나, 내가 볼 수 없는 메시지 */
    MESSAGE_NOT_FOUND(404, "메시지를 찾을 수 없어요."),
    /** 본문이 비어 있거나 공백뿐 */
    EMPTY_MESSAGE(400, "메시지를 입력해 주세요."),
    /** 본문이 2,000자(코드 포인트)를 넘음 */
    MESSAGE_TOO_LONG(400, "메시지는 2,000자까지 보낼 수 있어요."),
    /** 첨부(사진 · 장소 · 게시물)는 아직 보낼 수 없음 (별도 이슈) */
    ATTACHMENT_NOT_SUPPORTED(400, "아직 사진이나 장소는 보낼 수 없어요."),
    /** 그룹 이름이 비었거나 20자를 넘음 */
    INVALID_GROUP_TITLE(400, "채팅방 이름은 1~20자로 입력해 주세요."),
    /** 그룹을 만들 때 나를 빼고 2명 미만 */
    GROUP_TOO_SMALL(400, "그룹 채팅은 나를 포함해 3명 이상이어야 해요."),
    /** 그룹 인원이 10명을 넘게 됨 */
    GROUP_FULL(409, "그룹 채팅은 10명까지 참여할 수 있어요."),
    /** 1:1 대화방에 그룹 기능(이름 변경 · 초대)을 요청함 */
    NOT_A_GROUP(400, "그룹 채팅에서만 할 수 있어요.");

    private final int status;
    private final String message;

    ConversationErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
