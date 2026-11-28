package com.pickong.conversation.adapter.in.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.adapter.in.security.AuthCookies;
import com.pickong.auth.application.port.out.TokenIssuerPort;
import com.pickong.conversation.application.port.in.ConversationCommandUseCase;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 실제 서버에 STOMP로 붙어 보내기 · 받기 · 오류 · 읽음을 확인합니다 (common 5.9). REDIS_HOST가 없어 서버 안 전달로 동작합니다.
 * 서버가 다른 스레드 · 트랜잭션에서 읽어야 해서 롤백하지 않고, 만든 회원을 지워 정리합니다(대화 · 메시지는 CASCADE).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DmStompIntegrationTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:3000";
    private static final long WAIT_SECONDS = 5;

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    /** Access Token만 발급합니다 (refresh_tokens 행을 만들지 않아 회원을 지울 수 있음). */
    @Autowired
    TokenIssuerPort tokens;

    @Autowired
    ConversationCommandUseCase conversations;

    private WebSocketStompClient client;
    private final List<Long> users = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();

    private long a;
    private long b;
    private String bName;
    private long conversation;

    @BeforeEach
    void setUp() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        a = insertUser(randomUsername());
        bName = randomUsername();
        b = insertUser(bName);
        conversation = conversations.openDirect(a, bName).conversation().id();
    }

    @AfterEach
    void tearDown() {
        sessions.forEach(s -> {
            if (s.isConnected()) {
                s.disconnect();
            }
        });
        client.stop();
        users.forEach(id -> {
            jdbc.update("DELETE FROM user_terms_agreements WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        });
    }

    @Test
    @DisplayName("보내기: 저장하고, 상대는 isMine=false · 나는 isMine=true(전송 확인, clientMessageId 포함)로 받는다")
    void sendDelivers() throws Exception {
        Client me = connect(a);
        Client partner = connect(b);
        String clientMessageId = UUID.randomUUID().toString();

        me.session.send("/app/dm.send", sendBody(conversation, clientMessageId, "안녕"));

        Map<String, Object> received = partner.nextEvent();
        assertThat(received.get("type")).isEqualTo("MESSAGE_CREATED");
        Map<String, Object> data = data(received);
        assertThat(data.get("conversationId")).isEqualTo(String.valueOf(conversation));
        assertThat(data.get("senderId")).isEqualTo(publicIdOf(a));
        assertThat(data.get("isMine")).isEqualTo(false);
        assertThat(data.get("text")).isEqualTo("안녕");
        assertThat(data.get("attachment")).isNull();
        assertThat(data.get("clientMessageId")).isEqualTo(clientMessageId);
        assertThat(data.get("createdAt")).isInstanceOf(String.class);

        Map<String, Object> mine = data(me.nextEvent());
        assertThat(mine.get("isMine")).isEqualTo(true);
        assertThat(mine.get("id")).isEqualTo(data.get("id"));
        assertThat(mine.get("clientMessageId")).isEqualTo(clientMessageId);

        assertThat(countMessages()).isEqualTo(1);
        // 보낸 사람은 자기 메시지까지 읽은 상태
        assertThat(jdbc.queryForObject("SELECT last_read_message_id FROM conversation_members WHERE conversation_id = ? AND user_id = ?",
                Long.class, conversation, a)).isEqualTo(Long.parseLong((String) data.get("id")));
    }

    @Test
    @DisplayName("같은 clientMessageId로 다시 보내면 저장하지 않고 보낸 사람에게만 같은 메시지로 다시 확인")
    void duplicateIsNotStoredTwice() throws Exception {
        Client me = connect(a);
        Client partner = connect(b);
        String clientMessageId = UUID.randomUUID().toString();

        me.session.send("/app/dm.send", sendBody(conversation, clientMessageId, "한 번만"));
        String firstId = (String) data(partner.nextEvent()).get("id");
        me.drainEvents();

        me.session.send("/app/dm.send", sendBody(conversation, clientMessageId, "한 번만"));

        assertThat(data(me.nextEvent()).get("id")).isEqualTo(firstId);
        assertThat(partner.events.poll(500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(countMessages()).isEqualTo(1);
    }

    @Test
    @DisplayName("업무 오류는 /user/queue/errors로, HTTP와 같은 status · code · message에 clientMessageId를 담는다. 연결은 유지")
    void businessErrors() throws Exception {
        long other = insertUser(randomUsername());
        long notMine = conversations.openDirect(b, usernameOf(other)).conversation().id();
        Client me = connect(a);
        String clientMessageId = UUID.randomUUID().toString();

        me.session.send("/app/dm.send", sendBody(notMine, clientMessageId, "몰래"));
        Map<String, Object> error = me.nextError();
        assertThat(error).containsEntry("status", 404).containsEntry("code", "CONVERSATION_NOT_FOUND")
                .containsEntry("clientMessageId", clientMessageId);
        assertThat(error.get("message")).isEqualTo("대화를 찾을 수 없어요.");

        me.session.send("/app/dm.send", sendBody(conversation, UUID.randomUUID().toString(), "   "));
        assertThat(me.nextError()).containsEntry("code", "EMPTY_MESSAGE");

        Map<String, Object> withAttachment = sendBody(conversation, UUID.randomUUID().toString(), "사진");
        withAttachment.put("attachment", Map.of("type", "IMAGE", "url", "https://img"));
        me.session.send("/app/dm.send", withAttachment);
        assertThat(me.nextError()).containsEntry("code", "ATTACHMENT_NOT_SUPPORTED");

        me.session.send("/app/dm.send", sendBody(conversation, "not-a-uuid", "안녕"));
        assertThat(me.nextError()).containsEntry("code", "INVALID_INPUT").containsEntry("clientMessageId", "not-a-uuid");

        assertThat(me.session.isConnected()).isTrue();
        assertThat(countMessages()).isZero();
    }

    @Test
    @DisplayName("읽음: 상대와 내 다른 탭이 READ_UPDATED(읽은 회원의 공개 id)를 받는다")
    void readUpdates() throws Exception {
        Client me = connect(a);
        Client partner = connect(b);
        me.session.send("/app/dm.send", sendBody(conversation, UUID.randomUUID().toString(), "읽어 줘"));
        String messageId = (String) data(partner.nextEvent()).get("id");
        me.drainEvents();

        partner.session.send("/app/dm.read", Map.of("conversationId", String.valueOf(conversation), "lastReadMessageId", messageId));

        Map<String, Object> event = me.nextEvent();
        assertThat(event.get("type")).isEqualTo("READ_UPDATED");
        assertThat(data(event)).containsEntry("conversationId", String.valueOf(conversation))
                .containsEntry("userId", publicIdOf(b)).containsEntry("lastReadMessageId", messageId);
        assertThat(partner.nextEvent().get("type")).isEqualTo("READ_UPDATED");
    }

    @Test
    @DisplayName("1:1에서 나간 상대도 새 메시지를 받으면 다시 참여해 대화방이 보인다")
    void leftPartnerRejoinsOnNewMessage() throws Exception {
        conversations.leave(b, conversation);
        Client me = connect(a);
        Client partner = connect(b);

        me.session.send("/app/dm.send", sendBody(conversation, UUID.randomUUID().toString(), "돌아와"));

        assertThat(data(partner.nextEvent()).get("text")).isEqualTo("돌아와");
        assertThat(jdbc.queryForObject("SELECT left_at IS NULL FROM conversation_members WHERE conversation_id = ? AND user_id = ?",
                Boolean.class, conversation, b)).isTrue();
    }

    @Test
    @DisplayName("그룹: 만들기 안내가 모두에게, 글은 보낸 사람 정보와 함께 모두에게, 나가면 남은 사람에게 안내 · 나간 사람에게 CONVERSATION_UPDATED")
    void groupDelivery() throws Exception {
        String cName = randomUsername();
        long c = insertUser(cName);
        Client me = connect(a);
        Client partner = connect(b);
        Client third = connect(c);

        long group = conversations.createGroup(a, "모임", List.of(bName, cName)).id();
        for (Client client : List.of(me, partner, third)) {
            Map<String, Object> created = data(client.nextEvent());
            assertThat(created.get("type")).isEqualTo("SYSTEM");
            assertThat(((Map<?, ?>) created.get("system")).get("event")).isEqualTo("CREATED");
        }

        me.session.send("/app/dm.send", sendBody(group, UUID.randomUUID().toString(), "다들 안녕"));
        for (Client client : List.of(partner, third)) {
            Map<String, Object> text = data(client.nextEvent());
            assertThat(text.get("text")).isEqualTo("다들 안녕");
            assertThat(text.get("isMine")).isEqualTo(false);
            assertThat(((Map<?, ?>) text.get("sender")).get("username")).isEqualTo(usernameOf(a));
        }
        me.drainEvents();

        conversations.leave(c, group);
        for (Client client : List.of(me, partner)) {
            assertThat(((Map<?, ?>) data(client.nextEvent()).get("system")).get("event")).isEqualTo("LEFT");
        }
        Map<String, Object> updated = third.nextEvent();
        assertThat(updated.get("type")).isEqualTo("CONVERSATION_UPDATED");
        assertThat(data(updated).get("conversationId")).isEqualTo(String.valueOf(group));
    }

    @Test
    @DisplayName("허용되지 않은 목적지 구독 · 다른 회원 큐로 직접 보내기는 ERROR(FORBIDDEN_DESTINATION) 후 연결 종료")
    void forbiddenDestinations() throws Exception {
        Client subscriber = connect(a);
        subscriber.session.subscribe("/topic/everything", new MapFrameHandler(new LinkedBlockingQueue<>()));
        assertThat(subscriber.handler.errors.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("FORBIDDEN_DESTINATION");

        Client sender = connect(a);
        sender.session.send("/user/" + b + "/queue/dm", Map.of("type", "MESSAGE_CREATED"));
        assertThat(sender.handler.errors.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("FORBIDDEN_DESTINATION");
    }

    @Test
    @DisplayName("핸드셰이크: 쿠키가 없거나 다른 사이트(Origin)에서 오면 연결되지 않는다")
    void handshakeRejected() {
        WebSocketHttpHeaders noCookie = new WebSocketHttpHeaders();
        noCookie.setOrigin(FRONTEND_ORIGIN);
        assertThatThrownBy(() -> client.connectAsync(url(), noCookie, new StompSessionHandlerAdapter() {
        }).get(WAIT_SECONDS, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);

        WebSocketHttpHeaders otherOrigin = headers(a);
        otherOrigin.setOrigin("https://evil.example");
        assertThatThrownBy(() -> client.connectAsync(url(), otherOrigin, new StompSessionHandlerAdapter() {
        }).get(WAIT_SECONDS, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
    }

    // ---- STOMP 클라이언트 ----

    private Client connect(long userId) throws Exception {
        ErrorCapturingHandler handler = new ErrorCapturingHandler();
        StompSession session = client.connectAsync(url(), headers(userId), handler).get(WAIT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);
        Client c = new Client(session, handler, new LinkedBlockingQueue<>(), new LinkedBlockingQueue<>());
        session.subscribe("/user/queue/dm", new MapFrameHandler(c.events));
        session.subscribe("/user/queue/errors", new MapFrameHandler(c.errors));
        // 한 연결의 프레임은 받은 순서대로 처리되므로(preserveReceiveOrder), 이 오류가 오면 위 구독이 모두 등록된 것입니다.
        session.send("/app/dm.read", Map.of("conversationId", "0"));
        assertThat(c.nextError()).containsEntry("code", "INVALID_INPUT");
        return c;
    }

    private WebSocketHttpHeaders headers(long userId) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", AuthCookies.ACCESS_TOKEN + "=" + tokens.issueAccessToken(userId));
        headers.setOrigin(FRONTEND_ORIGIN);
        return headers;
    }

    private String url() {
        return "ws://localhost:" + port + "/api/v1/ws";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> event) {
        return (Map<String, Object>) event.get("data");
    }

    private static Map<String, Object> sendBody(long conversationId, String clientMessageId, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("conversationId", String.valueOf(conversationId));
        body.put("clientMessageId", clientMessageId);
        body.put("text", text);
        body.put("attachment", null);
        return body;
    }

    private record Client(StompSession session, ErrorCapturingHandler handler,
                          BlockingQueue<Map<String, Object>> events, BlockingQueue<Map<String, Object>> errors) {

        Map<String, Object> nextEvent() throws InterruptedException {
            Map<String, Object> event = events.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(event).as("이벤트를 받지 못함").isNotNull();
            return event;
        }

        Map<String, Object> nextError() throws InterruptedException {
            Map<String, Object> error = errors.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(error).as("오류를 받지 못함").isNotNull();
            return error;
        }

        /** 같은 서버에서는 전송 확인이 두 번(바로 보낸 것 + 중계) 올 수 있어 비웁니다. 프론트는 id로 중복을 없앱니다. */
        void drainEvents() throws InterruptedException {
            while (events.poll(300, TimeUnit.MILLISECONDS) != null) {
                // 비우기
            }
        }
    }

    private record MapFrameHandler(BlockingQueue<Map<String, Object>> queue) implements StompFrameHandler {

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handleFrame(StompHeaders headers, Object payload) {
            queue.add((Map<String, Object>) payload);
        }
    }

    /**
     * ERROR 프레임의 message 헤더(거절 코드)를 모읍니다. 본문이 비어 있어 변환에 실패하면 클라이언트가 handleFrame 대신
     * handleException으로 넘기므로 두 곳 모두에서 받습니다.
     */
    private static class ErrorCapturingHandler extends StompSessionHandlerAdapter {

        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            capture(headers);
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload,
                                    Throwable exception) {
            if (command == StompCommand.ERROR) {
                capture(headers);
            }
        }

        private void capture(StompHeaders headers) {
            String message = headers.getFirst("message");
            if (message != null) {
                errors.add(message);
            }
        }
    }

    // ---- 데이터 ----

    private long insertUser(String username) {
        long id = jdbc.queryForObject("INSERT INTO users (username, email) VALUES (?, ?) RETURNING id",
                Long.class, username, username + "@example.com");
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, now() FROM terms WHERE is_required = TRUE
                """, id);
        users.add(id);
        return id;
    }

    private String usernameOf(long userId) {
        return jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, userId);
    }

    private String publicIdOf(long userId) {
        return jdbc.queryForObject("SELECT CAST(public_id AS TEXT) FROM users WHERE id = ?", String.class, userId);
    }

    private int countMessages() {
        return jdbc.queryForObject("SELECT count(*) FROM messages WHERE conversation_id = ?", Integer.class, conversation);
    }

    private static String randomUsername() {
        return "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
