package com.pickong.auth.adapter.in.websocket;

import com.pickong.common.websocket.StompPrincipal;
import com.pickong.common.websocket.StompSessionRejector;
import com.pickong.common.websocket.WebSocketSessionAttributes;
import com.pickong.user.application.port.in.UserAccountUseCase;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * 클라이언트가 보낸 STOMP 프레임을 확인합니다. 어긋나면 ERROR 프레임(message 헤더 = 코드)을 보내고 연결을 닫으며,
 * 그 프레임은 처리하지 않습니다. 예외를 던지지 않는 이유는 {@link StompSessionRejector}에 있습니다.
 * <ul>
 *   <li>CONNECT: 핸드셰이크에서 정한 사용자가 있어야 함 (UNAUTHORIZED)</li>
 *   <li>CONNECT · SUBSCRIBE · SEND: Access Token 만료 시각이 지났으면 TOKEN_EXPIRED</li>
 *   <li>SUBSCRIBE: /user/queue/**만. 사용자 목적지는 본인에게만 전달되므로 대화방별 권한 검사가 필요 없음</li>
 *   <li>SEND: /app/**만 (다른 회원의 큐로 직접 보내기 차단). 보낼 때마다 계정 상태 확인 (ACCOUNT_UNAVAILABLE)</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StompDestinationGuard implements ChannelInterceptor {

    static final String SUBSCRIBE_PREFIX = "/user/queue/";
    static final String SEND_PREFIX = "/app/";

    private final UserAccountUseCase accounts;
    private final StompSessionRejector rejector;
    private final Clock clock;

    public StompDestinationGuard(UserAccountUseCase accounts, StompSessionRejector rejector, Clock clock) {
        this.accounts = accounts;
        this.rejector = rejector;
        this.clock = clock;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        String violation = violation(accessor);
        if (violation == null) {
            return message;
        }
        rejector.reject(accessor, violation);
        return null;
    }

    /** @return 거절 코드, 문제없으면 null */
    String violation(StompHeaderAccessor accessor) {
        return switch (accessor.getCommand()) {
            case CONNECT, STOMP -> accessor.getUser() == null ? StompSessionRejector.UNAUTHORIZED : expired(accessor);
            case SUBSCRIBE -> firstOf(expired(accessor), outside(accessor.getDestination(), SUBSCRIBE_PREFIX));
            case SEND -> firstOf(expired(accessor), outside(accessor.getDestination(), SEND_PREFIX), unavailable(accessor));
            default -> null; // DISCONNECT · UNSUBSCRIBE 등은 그대로
        };
    }

    private String expired(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object expiresAt = attributes == null ? null : attributes.get(WebSocketSessionAttributes.ACCESS_TOKEN_EXPIRES_AT);
        return expiresAt instanceof Instant at && clock.instant().isBefore(at) ? null : StompSessionRejector.TOKEN_EXPIRED;
    }

    private static String outside(String destination, String prefix) {
        return destination != null && destination.startsWith(prefix) && !destination.contains("..")
                ? null : StompSessionRejector.FORBIDDEN_DESTINATION;
    }

    /** 앞의 확인을 통과했을 때만 DB를 봅니다. */
    private String unavailable(StompHeaderAccessor accessor) {
        return accessor.getUser() != null && accounts.canLogin(StompPrincipal.userIdOf(accessor.getUser()))
                ? null : StompSessionRejector.ACCOUNT_UNAVAILABLE;
    }

    private static String firstOf(String... codes) {
        for (String code : codes) {
            if (code != null) {
                return code;
            }
        }
        return null;
    }
}
