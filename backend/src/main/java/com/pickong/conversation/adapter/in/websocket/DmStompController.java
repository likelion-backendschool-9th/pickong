package com.pickong.conversation.adapter.in.websocket;

import com.pickong.common.error.BusinessException;
import com.pickong.common.websocket.StompPrincipal;
import com.pickong.common.websocket.StompRequestException;
import com.pickong.conversation.adapter.in.websocket.payload.ReadPayload;
import com.pickong.conversation.adapter.in.websocket.payload.SendMessagePayload;
import com.pickong.conversation.application.port.in.MessageUseCase;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * DM STOMP 수신 (common 5.9). 결과는 응답이 아니라 커밋 후 /user/queue/dm 이벤트로 갑니다.
 * 오류는 공통 StompExceptionHandler가 /user/queue/errors로 보내고, 보내기 오류에는 clientMessageId를 담습니다.
 * 목적지 · 토큰 만료 · 계정 상태는 auth의 StompDestinationGuard가 먼저 확인합니다.
 */
@Controller
public class DmStompController {

    private final MessageUseCase messages;

    public DmStompController(MessageUseCase messages) {
        this.messages = messages;
    }

    /** SEND /app/dm.send */
    @MessageMapping("/dm.send")
    public void send(@Payload SendMessagePayload payload, Principal principal) {
        try {
            messages.send(payload.toCommand(StompPrincipal.userIdOf(principal)));
        } catch (BusinessException e) {
            throw new StompRequestException(e.getErrorCode(), payload.clientMessageId());
        }
    }

    /** SEND /app/dm.read */
    @MessageMapping("/dm.read")
    public void read(@Payload ReadPayload payload, Principal principal) {
        messages.markRead(StompPrincipal.userIdOf(principal), payload.conversationIdValue(), payload.messageId());
    }
}
