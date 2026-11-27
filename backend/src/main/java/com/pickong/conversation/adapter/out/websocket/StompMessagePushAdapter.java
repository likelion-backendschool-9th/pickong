package com.pickong.conversation.adapter.out.websocket;

import com.pickong.common.websocket.UserPushRelay;
import com.pickong.conversation.adapter.out.websocket.payload.DmEventPayload;
import com.pickong.conversation.application.dto.ReadReceipt;
import com.pickong.conversation.application.dto.SentMessage;
import com.pickong.conversation.application.port.out.MessagePushPort;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 대화 이벤트를 커밋 후에 /user/queue/dm으로 보냅니다 (blueprint 5.4.4 · 5.4.5).
 * <ul>
 *   <li>보낸 사람의 전송 확인(isMine=true)은 {@code sendLocal}로 이 서버에서 바로 보냅니다. 보낸 사람은 SEND를 처리한
 *       이 서버에 연결돼 있어 Redis 장애 중에도 확인이 갑니다</li>
 *   <li>모든 참여자(보낸 사람의 다른 서버 탭 포함)에게는 {@code publish}로 보냅니다. 같은 서버의 보낸 사람은 두 번 받을 수 있고
 *       프론트가 id로 중복을 없앱니다</li>
 * </ul>
 */
@Component
public class StompMessagePushAdapter implements MessagePushPort {

    static final String DM = "/queue/dm";

    private static final Logger log = LoggerFactory.getLogger(StompMessagePushAdapter.class);

    private final UserPushRelay relay;

    public StompMessagePushAdapter(UserPushRelay relay) {
        this.relay = relay;
    }

    @Override
    public void pushMessage(Collection<Long> recipientIds, SentMessage message) {
        List<Long> others = recipientIds.stream().filter(id -> id != message.senderId()).distinct().toList();
        afterCommit(() -> {
            DmEventPayload mine = DmEventPayload.messageCreated(message, true);
            relay.sendLocal(message.senderId(), DM, mine);
            relay.publish(List.of(message.senderId()), DM, mine);
            relay.publish(others, DM, DmEventPayload.messageCreated(message, false));
        });
    }

    @Override
    public void resendToSender(SentMessage message) {
        afterCommit(() -> relay.sendLocal(message.senderId(), DM, DmEventPayload.messageCreated(message, true)));
    }

    @Override
    public void pushRead(Collection<Long> recipientIds, ReadReceipt receipt) {
        List<Long> recipients = recipientIds.stream().distinct().toList();
        afterCommit(() -> relay.publish(recipients, DM, DmEventPayload.readUpdated(receipt)));
    }

    /** 트랜잭션 안이면 커밋 후에, 밖이면 바로 실행합니다. 전달 실패가 보내기 결과를 바꾸지 않게 예외는 로그만 남깁니다. */
    private static void afterCommit(Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("대화 이벤트 전달 실패", e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }
}
