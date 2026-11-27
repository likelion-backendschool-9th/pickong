package com.pickong.common.websocket;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * STOMP over WebSocket 설정 (blueprint 5.4.2 · 5.4.3, common 5.9).
 * <ul>
 *   <li>엔드포인트 "/ws", SockJS 없음. DispatcherServlet이 /api/v1에 매핑되어 있어 실제 주소는 <b>/api/v1/ws</b>이고,
 *       REST와 같은 보안 필터(인증 · 약관 재동의 · 계정 상태)를 거칩니다</li>
 *   <li>허용 Origin은 프론트 주소(pickong.frontend.base-url) 하나. 쿠키로 인증하므로 다른 사이트의 연결(CSWSH)을 막습니다</li>
 *   <li>보내기 /app/**, 받기 /user/queue/** (simple broker), heartbeat 10초</li>
 *   <li>인증 · 목적지 확인은 auth 모듈이 등록한 HandshakeInterceptor · ChannelInterceptor 빈을 붙이기만 합니다 (common은 auth를 import하지 않음)</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final long[] HEARTBEAT_MILLIS = {10_000, 10_000};
    /** 본문 2,000자 메시지에 여유를 둔 프레임 크기 상한 */
    static final int MESSAGE_SIZE_LIMIT = 64 * 1024;

    private final String allowedOrigin;
    private final List<HandshakeInterceptor> handshakeInterceptors;
    private final List<ChannelInterceptor> channelInterceptors;
    private final ExpiredSessionCloser expiredSessions;
    private TaskScheduler brokerScheduler;

    public WebSocketConfig(@Value("${pickong.frontend.base-url}") String frontendBaseUrl,
                           ObjectProvider<HandshakeInterceptor> handshakeInterceptors,
                           ObjectProvider<ChannelInterceptor> channelInterceptors,
                           ExpiredSessionCloser expiredSessions) {
        // 브라우저의 Origin 헤더에는 끝의 /가 없습니다.
        this.allowedOrigin = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
        this.handshakeInterceptors = handshakeInterceptors.orderedStream().toList();
        this.channelInterceptors = channelInterceptors.orderedStream().toList();
        this.expiredSessions = expiredSessions;
    }

    /** heartbeat용 스케줄러. 이 설정을 읽어 만드는 빈이라 순환을 피하려고 @Lazy로 받습니다 (Spring 문서의 방식). */
    @Autowired
    void setBrokerScheduler(@Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler scheduler) {
        this.brokerScheduler = scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigin)
                .setHandshakeHandler(new UserIdHandshakeHandler())
                .addInterceptors(handshakeInterceptors.toArray(HandshakeInterceptor[]::new));
        // 한 연결에서 온 프레임은 받은 순서대로 처리합니다. 빠르게 연달아 보낸 메시지가 뒤바뀌거나,
        // 구독보다 보내기가 먼저 처리되지 않게 합니다. 이 설정에서는 인터셉터가 예외로 거절하면 안 됩니다 (StompSessionRejector).
        registry.setPreserveReceiveOrder(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(HEARTBEAT_MILLIS)
                .setTaskScheduler(brokerScheduler);
        // 같은 세션으로 가는 메시지는 보낸 순서대로 (채팅 순서)
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(channelInterceptors.toArray(ChannelInterceptor[]::new));
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MESSAGE_SIZE_LIMIT);
        registration.addDecoratorFactory(expiredSessions::track);
    }
}
