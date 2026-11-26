package com.pickong.place.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.adapter.in.security.AuthCookies;
import com.pickong.auth.application.port.in.TokenUseCase;
import com.pickong.common.error.BusinessException;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.place.application.dto.SubmitPlaceCommand;
import com.pickong.place.application.port.in.PlaceSubmissionUseCase;
import com.pickong.place.domain.PlaceErrorCode;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 제보 HTTP와 DB를 한 트랜잭션으로 확인합니다. PostGIS 컨테이너가 필요합니다.
 * 도메인 · 서비스 단위 테스트는 이 클래스가 아니라 Docker 없는 테스트에 있습니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaceSubmissionIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** submit()이 보내는 좌표(37.5628, 126.9254)를 감싸는 지도 범위. /places는 bbox가 필수입니다. */
    private static final String AROUND_SUBMISSION = "126.92,37.56,126.93,37.57";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long adminId;

    @BeforeEach
    void activeAdminFixture() {
        adminId = jdbc.queryForObject("INSERT INTO admins(username,password_hash,role,status) VALUES (?,'test-only','OPERATOR','ACTIVE') RETURNING id",
                Long.class, "place-security-" + UUID.randomUUID());
    }

    @Autowired
    TokenUseCase tokenUseCase;

    @Autowired
    PlaceSubmissionUseCase submissions;

    /** 이 클래스는 커밋합니다. 남은 제보가 있으면 다른 테스트의 회원 삭제가 외래 키에 막힙니다. */
    @AfterEach
    void deleteCommittedRows() {
        jdbc.update("DELETE FROM admins WHERE id=?", adminId);
        jdbc.update("DELETE FROM place_submissions");
        jdbc.update("DELETE FROM places");
    }

    @Test
    @DisplayName("접수 직후 공개 장소에 없고, 승인 후에만 연결된다")
    void submitThenApproveAppearsOnPublicMap() throws Exception {
        long userId = insertUser();
        Cookie member = accessCookie(userId);
        String name = uniqueName("승인");

        String id = submit(member, name);
        assertThat(placesNamed(name)).isZero();
        mvc.perform(api(get("/api/v1/places")).param("bbox", AROUND_SUBMISSION).param("cluster", "false").cookie(member))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(name))));

        MvcResult approved = mvc.perform(api(post("/api/v1/admin/place-submissions/" + id + "/approve"))
                        .with(authentication(admin()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.placeId").isNotEmpty())
                .andReturn();
        String placeId = JSON.readTree(approved.getResponse().getContentAsString()).get("placeId").asString();

        assertThat(placesNamed(name)).isEqualTo(1);
        String linked = jdbc.queryForObject("SELECT place_id::text FROM place_submissions WHERE id = ?", String.class, Long.valueOf(id));
        assertThat(linked).isNotBlank();
        mvc.perform(api(get("/api/v1/places")).param("bbox", AROUND_SUBMISSION).param("cluster", "false").cookie(member))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(placeId)));
    }

    @Test
    @DisplayName("반려하면 사유만 남고 공개 장소는 생기지 않는다")
    void rejectDoesNotPublish() throws Exception {
        long userId = insertUser();
        Cookie member = accessCookie(userId);
        String name = uniqueName("반려");
        String id = submit(member, name);

        mvc.perform(api(post("/api/v1/admin/place-submissions/" + id + "/reject"))
                        .with(authentication(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"위치가 불명확합니다\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("위치가 불명확합니다"))
                .andExpect(jsonPath("$.placeId").value(nullValue()));

        assertThat(placesNamed(name)).isZero();
        assertThat(jdbc.queryForObject("SELECT place_id FROM place_submissions WHERE id = ?", Long.class, Long.valueOf(id))).isNull();
    }

    @Test
    @DisplayName("없는 제보는 404, 빈 반려 사유와 좌표 없음은 400, 재승인은 409")
    void httpErrors() throws Exception {
        long userId = insertUser();
        Cookie member = accessCookie(userId);

        mvc.perform(api(get("/api/v1/admin/place-submissions/999999999")).with(authentication(admin())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_SUBMISSION_NOT_FOUND"));

        mvc.perform(api(post("/api/v1/place-submissions"))
                        .with(csrf())
                        .cookie(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"좌표없음\",\"category\":\"SHOP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"));

        String id = submit(member, uniqueName("재처리"));
        mvc.perform(api(post("/api/v1/admin/place-submissions/" + id + "/reject"))
                        .with(authentication(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REJECT_REASON_REQUIRED"));

        mvc.perform(api(post("/api/v1/admin/place-submissions/" + id + "/approve"))
                        .with(authentication(admin()))
                        .with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(api(post("/api/v1/admin/place-submissions/" + id + "/approve"))
                        .with(authentication(admin()))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REVIEWED"));
    }

    @Test
    @DisplayName("같은 제보를 동시에 승인하면 공개 장소는 하나만 남는다")
    void concurrentApproveRollsBackTheLoser() throws Exception {
        long userId = insertUser();
        String name = uniqueName("동시승인");
        long id = submissions.submit(command(userId, name)).submission().getId();

        List<Object> outcomes = race(
                () -> submissions.approve(id, 7L),
                () -> submissions.approve(id, 8L));

        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(conflictCount(outcomes)).isEqualTo(1);
        assertThat(placesNamed(name)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM place_submissions WHERE id = ?", String.class, id)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("같은 제보를 동시에 승인과 반려하면 한 결과만 남는다")
    void concurrentApproveAndReject() throws Exception {
        long userId = insertUser();
        String name = uniqueName("동시분기");
        long id = submissions.submit(command(userId, name)).submission().getId();

        List<Object> outcomes = race(
                () -> submissions.approve(id, 7L),
                () -> submissions.reject(id, 8L, "위치가 불명확합니다"));

        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(conflictCount(outcomes)).isEqualTo(1);
        String status = jdbc.queryForObject("SELECT status FROM place_submissions WHERE id = ?", String.class, id);
        if ("APPROVED".equals(status)) {
            assertThat(placesNamed(name)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT place_id FROM place_submissions WHERE id = ?", Long.class, id)).isNotNull();
        } else {
            assertThat(status).isEqualTo("REJECTED");
            assertThat(placesNamed(name)).isZero();
            assertThat(jdbc.queryForObject("SELECT place_id FROM place_submissions WHERE id = ?", Long.class, id)).isNull();
        }
    }

    @Test
    @DisplayName("공개 장소 저장이 실패하면 제보는 대기 상태로 남는다")
    void placeSaveFailureRollsBack() {
        long userId = insertUser();
        String name = uniqueName("롤백");
        long id = submissions.submit(command(userId, name)).submission().getId();
        jdbc.execute("ALTER TABLE places ADD CONSTRAINT ck_places_step12_fail CHECK (name <> '" + name + "')");
        try {
            assertThatThrownBy(() -> submissions.approve(id, 7L)).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("ALTER TABLE places DROP CONSTRAINT IF EXISTS ck_places_step12_fail");
        }
        assertThat(jdbc.queryForObject("SELECT status FROM place_submissions WHERE id = ?", String.class, id)).isEqualTo("PENDING");
        assertThat(placesNamed(name)).isZero();
    }

    private String submit(Cookie member, String name) throws Exception {
        MvcResult result = mvc.perform(api(post("/api/v1/place-submissions"))
                        .with(csrf())
                        .cookie(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","lat":37.5628,"lng":126.9254,"description":"설명","category":"SHOP"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.placeId").value(nullValue()))
                .andReturn();
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        return body.get("id").asString();
    }

    private List<Object> race(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> left = pool.submit(() -> runRace(ready, start, first));
            Future<Object> right = pool.submit(() -> runRace(ready, start, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            outcomes.add(left.get(10, TimeUnit.SECONDS));
            outcomes.add(right.get(10, TimeUnit.SECONDS));
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private Object runRace(CountDownLatch ready, CountDownLatch start, Runnable action) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return new IllegalStateException("동시 실행을 시작하지 못했습니다.");
            }
            action.run();
            return "ok";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return exception;
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private static long successCount(List<Object> outcomes) {
        return outcomes.stream().filter("ok"::equals).count();
    }

    private static long conflictCount(List<Object> outcomes) {
        return outcomes.stream()
                .filter(BusinessException.class::isInstance)
                .map(BusinessException.class::cast)
                .filter(exception -> exception.getErrorCode() == PlaceErrorCode.ALREADY_REVIEWED)
                .count();
    }

    private long insertUser() {
        String username = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return jdbc.queryForObject("INSERT INTO users (username) VALUES (?) RETURNING id", Long.class, username);
    }

    private Cookie accessCookie(long userId) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenUseCase.issue(userId).accessToken());
    }

    private int placesNamed(String name) {
        return jdbc.queryForObject("SELECT count(*) FROM places WHERE name = ?", Integer.class, name);
    }

    private static SubmitPlaceCommand command(long userId, String name) {
        return new SubmitPlaceCommand(userId, name, "서울", "SHOP", "설명", null, 37.5628, 126.9254);
    }

    private static String uniqueName(String label) {
        return label + UUID.randomUUID().toString().substring(0, 8);
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private UsernamePasswordAuthenticationToken admin() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(adminId), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
