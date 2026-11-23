package com.pickong.auth.adapter.in.security;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * 테스트용 <b>가짜 카카오 서버</b>(JDK 내장 HttpServer). 토큰 · 사용자 정보 API만 흉내 냅니다.
 * 여러 테스트 클래스가 같은 서버(같은 포트)를 써서 Spring 테스트 컨텍스트도 함께 재사용됩니다.
 * 서버는 테스트 JVM이 끝날 때 함께 내려갑니다.
 */
final class FakeKakao {

    private static volatile String userJson = user(4242, "kong@kakao.com", true);

    private static final HttpServer SERVER = start();

    private FakeKakao() {
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/oauth/token", exchange -> respond(exchange,
                    "{\"access_token\":\"fake-kakao-access-token\",\"token_type\":\"bearer\",\"expires_in\":3600}"));
            server.createContext("/v2/user/me", exchange -> respond(exchange, userJson));
            server.setExecutor(null);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /** 카카오의 토큰 · 사용자 정보 주소만 가짜 서버로 바꿉니다. (동의 화면 주소는 그대로) @DynamicPropertySource에서 부릅니다 */
    static void register(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + SERVER.getAddress().getPort();
        registry.add("spring.security.oauth2.client.provider.kakao.token-uri", () -> base + "/oauth/token");
        registry.add("spring.security.oauth2.client.provider.kakao.user-info-uri", () -> base + "/v2/user/me");
        registry.add("spring.security.oauth2.client.registration.kakao.client-id", () -> "test-kakao-client");
        registry.add("spring.security.oauth2.client.registration.kakao.client-secret", () -> "test-kakao-secret");
    }

    /** 다음 사용자 정보 응답을 바꿉니다. */
    static void respondWith(String json) {
        userJson = json;
    }

    /** 카카오 사용자 정보 응답(JSON) */
    static String user(long id, String email, boolean verified) {
        return """
                {"id": %d,
                 "kakao_account": {"email": "%s", "is_email_valid": true, "is_email_verified": %s,
                   "profile": {"nickname": "콩이", "profile_image_url": "http://k.kakaocdn.net/dn/a.jpg", "is_default_image": false}}}
                """.formatted(id, email, verified);
    }
}
