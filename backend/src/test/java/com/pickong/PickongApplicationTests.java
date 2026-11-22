package com.pickong;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 앱 전체가 실제 PostgreSQL + Flyway 마이그레이션 위에서 뜨는지 확인합니다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PickongApplicationTests {

    @Test
    void contextLoads() {
    }
}
