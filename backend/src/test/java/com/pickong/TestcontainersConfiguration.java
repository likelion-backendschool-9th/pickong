package com.pickong;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * DB가 필요한 테스트용 PostgreSQL(PostGIS 16) 컨테이너. 운영 · 로컬(compose)과 같은 이미지입니다.
 * <p>
 * 사용법: 테스트 클래스에 {@code @Import(TestcontainersConfiguration.class)}.
 * {@code @ServiceConnection}이 컨테이너 주소를 datasource에 연결하고, 앱이 뜰 때 Flyway 마이그레이션이 그대로 적용됩니다.
 * 같은 설정을 쓰는 테스트들은 Spring 테스트 컨텍스트 캐시 덕분에 컨테이너 하나를 함께 씁니다.
 * <p>
 * Docker가 꺼져 있으면 이 설정을 쓰는 테스트는 실패합니다. (건너뛰지 않음)
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final DockerImageName POSTGIS = DockerImageName.parse("postgis/postgis:16-3.5")
            .asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGIS);
    }
}
