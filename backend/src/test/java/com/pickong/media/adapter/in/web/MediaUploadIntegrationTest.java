package com.pickong.media.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.LoginUser;
import com.pickong.media.application.port.out.ObjectStoragePort;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사진 업로드 API를 실제 보안 필터와 DB(V21 media_files)로 확인함
 * R2만 메모리 가짜(FakeStorage)로 바꿔서 네트워크 없이 돌아감. PostGIS 컨테이너가 필요함
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MediaUploadIntegrationTest.FakeStorageConfig.class})
@Transactional
class MediaUploadIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeStorage storage;

    @Autowired
    EntityManager entityManager;

    private long me;
    private long other;

    @BeforeEach
    void setUp() {
        me = insertUser();
        other = insertUser();
    }

    @Test
    @DisplayName("발급: 201, PENDING 기록이 로그인 사용자 이름으로 저장되고 Presigned URL과 Content-Type을 돌려줌")
    void issue() throws Exception {
        String body = issue(me, "image/jpeg", 2048)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andReturn().getResponse().getContentAsString();
        long mediaId = ((Number) JsonPath.read(body, "$.mediaId")).longValue();

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM media_files WHERE id = ?", mediaId);
        assertThat(row.get("user_id")).isEqualTo(me);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("size_bytes")).isEqualTo(2048L);
        assertThat((String) row.get("storage_key")).startsWith("feeds/").endsWith(".jpg");
        assertThat(JsonPath.<String>read(body, "$.uploadUrl")).isEqualTo("signed:" + row.get("storage_key"));
    }

    @Test
    @DisplayName("발급: 허용하지 않는 종류 400 MEDIA_TYPE_NOT_ALLOWED, 10MB 초과 400 MEDIA_TOO_LARGE, 기록은 안 남음")
    void issueRejectsInvalidFile() throws Exception {
        issue(me, "image/gif", 2048)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_TYPE_NOT_ALLOWED"));
        issue(me, "image/jpeg", 10L * 1024 * 1024 + 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_TOO_LARGE"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_files WHERE user_id = ?", Integer.class, me)).isZero();
    }

    @Test
    @DisplayName("발급: 크기가 없으면 400")
    void issueRequiresSize() throws Exception {
        mvc.perform(post("/api/v1/media/presigned").servletPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\": \"image/jpeg\"}")
                        .with(csrf())
                        .with(authentication(member(me))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("발급, 확인 모두 로그인하지 않으면 401")
    void requiresLogin() throws Exception {
        mvc.perform(post("/api/v1/media/presigned").servletPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\": \"image/jpeg\", \"sizeBytes\": 2048}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/media/1/confirm").servletPath("/api/v1").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("확인 성공: R2에 같은 파일이 있으면 200, DB가 UPLOADED와 공개 주소로 바뀜")
    void confirm() throws Exception {
        long mediaId = issueAndGetId(me, "image/png", 4096);
        String key = jdbc.queryForObject("SELECT storage_key FROM media_files WHERE id = ?", String.class, mediaId);
        storage.put(key, 4096, "image/png");

        confirm(me, mediaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaId").value(mediaId))
                .andExpect(jsonPath("$.url").value("https://pub.example/" + key))
                .andExpect(jsonPath("$.sizeBytes").value(4096));

        // 테스트 트랜잭션 안이라 JPA가 아직 보내지 않은 UPDATE를 JDBC로 읽기 전에 내보냄
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM media_files WHERE id = ?", String.class, mediaId))
                .isEqualTo("UPLOADED");
    }

    @Test
    @DisplayName("확인: 남의 사진은 404 MEDIA_NOT_FOUND, 상태는 그대로")
    void confirmOthersMedia() throws Exception {
        long mediaId = issueAndGetId(me, "image/png", 4096);

        confirm(other, mediaId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT status FROM media_files WHERE id = ?", String.class, mediaId))
                .isEqualTo("PENDING");
    }

    /**
     * 오류를 돌려줘도 FAILED가 커밋되는지 보려면 진짜 커밋이 일어나야 해서 테스트 트랜잭션을 끄고 돌림
     * 그래서 이 테스트가 만든 행은 끝에 직접 지움
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("확인: 크기가 다르면 400 MEDIA_FILE_MISMATCH, 오류를 돌려줘도 FAILED는 커밋됨 (롤백 안 됨)")
    void confirmMismatchCommitsFailed() throws Exception {
        try {
            long mediaId = issueAndGetId(me, "image/png", 4096);
            String key = jdbc.queryForObject("SELECT storage_key FROM media_files WHERE id = ?", String.class, mediaId);
            storage.put(key, 1, "image/png");

            confirm(me, mediaId)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MEDIA_FILE_MISMATCH"));

            assertThat(jdbc.queryForObject("SELECT status FROM media_files WHERE id = ?", String.class, mediaId))
                    .isEqualTo("FAILED");
            assertThat(storage.findObject(key)).isEmpty();
        } finally {
            for (long userId : List.of(me, other)) {
                jdbc.update("DELETE FROM media_files WHERE user_id = ?", userId);
                jdbc.update("DELETE FROM user_terms_agreements WHERE user_id = ?", userId);
                jdbc.update("DELETE FROM users WHERE id = ?", userId);
            }
        }
    }

    private ResultActions issue(long userId, String contentType, long sizeBytes) throws Exception {
        return mvc.perform(post("/api/v1/media/presigned").servletPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\": \"" + contentType + "\", \"sizeBytes\": " + sizeBytes + "}")
                .with(csrf())
                .with(authentication(member(userId))));
    }

    private long issueAndGetId(long userId, String contentType, long sizeBytes) throws Exception {
        String body = issue(userId, contentType, sizeBytes).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.mediaId")).longValue();
    }

    private ResultActions confirm(long userId, long mediaId) throws Exception {
        return mvc.perform(post("/api/v1/media/" + mediaId + "/confirm").servletPath("/api/v1")
                .with(csrf())
                .with(authentication(member(userId))));
    }

    private long insertUser() {
        String username = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long id = jdbc.queryForObject("INSERT INTO users (username, email) VALUES (?, ?) RETURNING id",
                Long.class, username, username + "@example.com");
        // 필수 약관 재동의 필터에 걸리지 않도록 동의해 둠
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, now() FROM terms WHERE is_required = TRUE
                """, id);
        return id;
    }

    private static UsernamePasswordAuthenticationToken member(long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @TestConfiguration
    static class FakeStorageConfig {
        // 진짜 R2StorageAdapter 대신 이 가짜를 쓰게 함
        @Bean
        @Primary
        FakeStorage fakeStorage() {
            return new FakeStorage();
        }
    }

    /** R2 대신 메모리 Map */
    static class FakeStorage implements ObjectStoragePort {
        private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

        void put(String key, long size, String type) {
            objects.put(key, new StoredObject(size, type));
        }

        @Override
        public String createUploadUrl(String key, String contentType, long contentLength) {
            return "signed:" + key;
        }

        @Override
        public Optional<StoredObject> findObject(String key) {
            return Optional.ofNullable(objects.get(key));
        }

        @Override
        public void deleteObject(String key) {
            objects.remove(key);
        }

        @Override
        public String publicUrl(String key) {
            return "https://pub.example/" + key;
        }
    }
}
