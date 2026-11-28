package com.pickong.conversation.application.port.out;

import com.pickong.conversation.application.dto.ReadReceipt;
import com.pickong.conversation.application.dto.SentMessage;
import java.util.Collection;

/**
 * 실시간 전달 (/user/queue/dm). 구현은 트랜잭션 커밋 후에 보냅니다. 커밋 전에 보내면 롤백된 메시지가 상대 화면에 나타날 수 있습니다.
 * 전달 실패는 예외로 알리지 않습니다(메시지 원본은 DB, 클라이언트는 다시 연결할 때 보충).
 */
public interface MessagePushPort {

    /** 새 메시지(MESSAGE_CREATED)를 받는 사람들에게. 보낸 사람에게는 전송 확인을 이 서버에서 바로 보냅니다. */
    void pushMessage(Collection<Long> recipientIds, SentMessage message);

    /** 같은 clientMessageId로 다시 보냈을 때: 저장하지 않고 보낸 사람에게만 다시 확인을 보냅니다. */
    void resendToSender(SentMessage message);

    /** 읽음 위치가 바뀜(READ_UPDATED) */
    void pushRead(Collection<Long> recipientIds, ReadReceipt receipt);

    /** 그룹 안내 메시지(MESSAGE_CREATED). 보낸 사람 전송 확인은 없습니다(사람이 보낸 글이 아님). */
    void pushSystemMessage(Collection<Long> recipientIds, SentMessage message);

    /** 대화방 정보가 바뀜(CONVERSATION_UPDATED) — 나간 사람의 다른 탭처럼 메시지를 더 받지 않는 회원에게 */
    void pushConversationUpdated(Collection<Long> userIds, long conversationId);
}
