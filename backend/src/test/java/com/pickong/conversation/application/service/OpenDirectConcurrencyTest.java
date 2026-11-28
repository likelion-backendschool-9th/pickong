package com.pickong.conversation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickong.TestcontainersConfiguration;
import com.pickong.conversation.application.dto.OpenDirectResult;
import com.pickong.conversation.application.port.in.ConversationCommandUseCase;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 두 사람이 동시에 서로와의 1:1 대화방을 열어도 대화방은 하나만 생긴다 (direct_key UNIQUE + INSERT ... ON CONFLICT).
 * 트랜잭션 두 개가 실제로 겹쳐야 하므로 롤백하지 않고, 만든 회원을 지워 정리합니다(대화방은 CASCADE로 함께 지워짐).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OpenDirectConcurrencyTest {

    @Autowired
    ConversationCommandUseCase commands;

    @Autowired
    JdbcTemplate jdbc;

    private Long a;
    private Long b;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", a, b);
    }

    @RepeatedTest(5)
    @DisplayName("동시에 열면 같은 대화방을 받고, 하나만 '새로 만듦'")
    void concurrentOpenCreatesOne() throws Exception {
        String aName = randomUsername();
        String bName = randomUsername();
        a = insertUser(aName);
        b = insertUser(bName);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<OpenDirectResult> fromA = pool.submit(() -> {
                start.await();
                return commands.openDirect(a, bName);
            });
            Future<OpenDirectResult> fromB = pool.submit(() -> {
                start.await();
                return commands.openDirect(b, aName);
            });
            start.countDown();

            List<OpenDirectResult> results = List.of(fromA.get(10, TimeUnit.SECONDS), fromB.get(10, TimeUnit.SECONDS));

            assertThat(results).extracting(r -> r.conversation().id()).containsOnly(results.getFirst().conversation().id());
            assertThat(results).filteredOn(OpenDirectResult::created).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM conversations WHERE direct_key = ?", Integer.class,
                    Math.min(a, b) + ":" + Math.max(a, b))).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM conversation_members WHERE conversation_id = ?",
                    Integer.class, results.getFirst().conversation().id())).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    private long insertUser(String username) {
        return jdbc.queryForObject("INSERT INTO users (username, email) VALUES (?, ?) RETURNING id",
                Long.class, username, username + "@example.com");
    }

    private static String randomUsername() {
        return "x" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
