package com.pickong.feed.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.common.security.LoginUser;
import com.pickong.feed.application.dto.UpdateFeedCommand;
import com.pickong.feed.application.port.in.FeedModerationUseCase;
import com.pickong.feed.application.port.in.FeedUseCase;
import com.pickong.feed.application.port.in.FeedReactionUseCase;
import com.pickong.common.error.BusinessException;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.common.domain.Visibility;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실 보안 체인·동기 로그·커밋·행 잠금을 격리된 PostgreSQL에서 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FeedModerationIntegrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T03:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired FeedModerationUseCase moderation;
    @Autowired FeedUseCase feeds;
    @Autowired FeedReactionUseCase reactions;
    @Autowired FeedRepository repository;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean Clock clock;
    private long owner;
    private long other;
    private long admin;
    private long secondAdmin;
    private long feed;
    private long comment;
    private long report;
    private UUID publicId;

    @BeforeEach
    void fixtures() {
        given(clock.instant()).willReturn(NOW.toInstant());
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
        owner = user(); other = user(); admin = admin(); secondAdmin = admin();
        for (long user : List.of(owner, other)) {
            jdbc.update("INSERT INTO user_terms_agreements(user_id,terms_id,agreed_at) SELECT ?,id,? FROM terms WHERE is_required=true", user, NOW);
        }
        feed = jdbc.queryForObject("INSERT INTO feeds(user_id,content,visibility,like_count,bookmark_count,comment_count) VALUES (?,'제목\n원문','PUBLIC',1,1,2) RETURNING id", Long.class, owner);
        publicId = jdbc.queryForObject("SELECT public_id FROM feeds WHERE id=?", UUID.class, feed);
        comment = jdbc.queryForObject("INSERT INTO comments(feed_id,user_id,content) VALUES (?,?,'댓글') RETURNING id", Long.class, feed, owner);
        jdbc.update("INSERT INTO comments(feed_id,user_id,parent_id,content) VALUES (?,?,?,'대댓글')", feed, other, comment);
        jdbc.update("INSERT INTO feed_likes(feed_id,user_id) VALUES (?,?)", feed, owner);
        jdbc.update("INSERT INTO feed_bookmarks(feed_id,user_id) VALUES (?,?)", feed, owner);
        jdbc.update("INSERT INTO comment_likes(comment_id,user_id) VALUES (?,?)", comment, owner);
        jdbc.update("INSERT INTO feed_photos(feed_id,media_file_id,sort_order) VALUES (?,101,0)", feed);
        jdbc.update("INSERT INTO feed_places(feed_id,place_id) VALUES (?,201)", feed);
        report = jdbc.queryForObject("INSERT INTO reports(reporter_id,target_type,target_id,reason,snapshot) VALUES (?,'FEED',?,'SPAM','original snapshot') RETURNING id", Long.class, other, feed);
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("ALTER TABLE admin_action_logs DROP CONSTRAINT IF EXISTS ck_s10_reject_reason");
        jdbc.update("DELETE FROM admin_action_logs WHERE admin_id IN (?,?)", admin, secondAdmin);
        jdbc.update("DELETE FROM reports WHERE id=?", report);
        jdbc.update("DELETE FROM feeds WHERE id=?", feed);
        jdbc.update("DELETE FROM user_terms_agreements WHERE user_id IN (?,?)", owner, other);
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", owner, other);
        jdbc.update("DELETE FROM admins WHERE id IN (?,?)", admin, secondAdmin);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLIC", "FOLLOWERS", "PRIVATE"})
    void hideRestoreAndNoopsPreserveOriginalVisibilityAndData(String visibility) throws Exception {
        jdbc.update("UPDATE feeds SET visibility=? WHERE id=?", visibility, feed);
        String before = immutableData();
        patchState(true, "  숨김 사유  ").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(publicId.toString()))
                .andExpect(jsonPath("$.hidden").value(true)).andExpect(jsonPath("$.visibility").value(visibility))
                .andExpect(jsonPath("$.reportCount").value(1));
        assertThat(hiddenAt()).isEqualTo(NOW);
        assertThat(logs()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reason FROM admin_action_logs WHERE target_id=?", String.class, feed)).isEqualTo("숨김 사유");
        String changed = jdbc.queryForObject("SELECT updated_at::text FROM feeds WHERE id=?", String.class, feed);
        given(clock.instant()).willReturn(NOW.plusHours(1).toInstant());
        patchState(true, "새 사유").andExpect(status().isOk());
        assertThat(hiddenAt()).isEqualTo(NOW);
        assertThat(jdbc.queryForObject("SELECT updated_at::text FROM feeds WHERE id=?", String.class, feed)).isEqualTo(changed);
        assertThat(logs()).isEqualTo(1);
        patchState(false, "  복구 사유  ").andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(false));
        patchState(false, null).andExpect(status().isOk());
        assertThat(hiddenAt()).isNull();
        assertThat(logs()).isEqualTo(2);
        assertThat(immutableData()).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT action_type FROM admin_action_logs WHERE target_id=? ORDER BY id", String.class, feed))
                .containsExactly("HIDE_CONTENT", "RESTORE_CONTENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feed_photos WHERE feed_id=?", Long.class, feed)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feed_places WHERE feed_id=?", Long.class, feed)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feed_likes WHERE feed_id=?", Long.class, feed)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feed_bookmarks WHERE feed_id=?", Long.class, feed)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comments WHERE feed_id=?", Long.class, feed)).isEqualTo(2);
        assertReportUnchanged();
    }

    @Test
    void restoreReasonIsOptionalAndActorAndCauseCannotBeSpoofed() throws Exception {
        mvc.perform(patchRequest().content("{\"hidden\":true,\"reason\":\"직접 처리\",\"adminId\":" + secondAdmin + ",\"sourceReportId\":" + report + "}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT admin_id FROM admin_action_logs WHERE target_id=?", Long.class, feed)).isEqualTo(admin);
        assertThat(jdbc.queryForObject("SELECT source_report_id FROM admin_action_logs WHERE target_id=?", Long.class, feed)).isNull();
        patchState(false, null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT reason FROM admin_action_logs WHERE target_id=? AND action_type='RESTORE_CONTENT'", String.class, feed)).isNull();
        assertReportUnchanged();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\u00a0\u2003\u0085"})
    void hidingRequiresNonblankReason(String reason) throws Exception {
        patchState(true, reason).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FEED_HIDE_REASON_REQUIRED"));
        assertThat(hiddenAt()).isNull(); assertThat(logs()).isZero();
    }

    @Test
    void reasonLengthCountsUnicodeCodePointsForBothOperations() throws Exception {
        patchState(true, "😀".repeat(201)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FEED_MODERATION_REASON_TOO_LONG"));
        patchState(true, "😀".repeat(200)).andExpect(status().isOk());
        patchState(false, "😀".repeat(201)).andExpect(status().isBadRequest());
        assertThat(hiddenAt()).isNotNull(); assertThat(logs()).isEqualTo(1);
        patchState(false, "😀".repeat(200)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "{}", "{\"hidden\":null}", "{\"hidden\":\"true\"}", "{\"hidden\":1}", "{\"hidden\":true,\"reason\":12}"})
    void malformedInputUsesCommonErrorContract(String body) throws Exception {
        mvc.perform(patchRequest().content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(hiddenAt()).isNull(); assertThat(logs()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "not-a-uuid", "1-1-1-1-1"})
    void pathMustBeCanonicalUuid(String id) throws Exception {
        mvc.perform(asAdmin(patch("/api/v1/admin/feeds/" + id)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(true, "사유")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void nonexistentAndDeletedContentCannotBeHiddenOrRestored() throws Exception {
        mvc.perform(asAdmin(patch("/api/v1/admin/feeds/" + UUID.randomUUID())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(true, "사유")))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE feeds SET deleted_at=? WHERE id=?", NOW, feed);
        patchState(true, "사유").andExpect(status().isNotFound());
        patchState(false, null).andExpect(status().isNotFound());
        mvc.perform(asMember(get("/api/v1/feeds/" + feed), owner)).andExpect(status().isNotFound());
        mvc.perform(asAdmin(get("/api/v1/feeds/" + feed))).andExpect(status().isNotFound());
        assertThat(logs()).isZero();
    }

    @Test
    void ownerAndAdminCanReadHiddenDetailButOthersAndSpoofedQueryCannot() throws Exception {
        patchState(true, "사유").andExpect(status().isOk());
        String path = "/api/v1/feeds/" + feed;
        mvc.perform(api(get(path))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FEED_NOT_FOUND"));
        mvc.perform(asMember(get(path), other).param("viewerId", Long.toString(owner)).param("admin", "true")).andExpect(status().isNotFound());
        mvc.perform(asMember(get(path), owner)).andExpect(status().isOk()).andExpect(jsonPath("$.content").value("제목\n원문"));
        mvc.perform(asAdmin(get(path))).andExpect(status().isOk());
        patchState(false, null).andExpect(status().isOk());
        mvc.perform(api(get(path))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/1/replies"})
    void commentEndpointsCannotBypassHiddenParent(String suffix) throws Exception {
        patchState(true, "사유").andExpect(status().isOk());
        String tail = suffix.isEmpty() ? "" : "/" + comment + "/replies";
        String path = "/api/v1/feeds/" + feed + "/comments" + tail;
        mvc.perform(asMember(get(path), other).param("limit", "20")).andExpect(status().isNotFound());
        mvc.perform(asMember(get(path), owner).param("limit", "20")).andExpect(status().isOk());
        mvc.perform(asAdmin(get(path)).param("limit", "20")).andExpect(status().isOk());
    }

    @Test
    void recommendedListExcludesHiddenAndRestorationMakesItReappear() throws Exception {
        patchState(true, "사유").andExpect(status().isOk());
        String before = mvc.perform(asMember(get("/api/v1/feeds"), other).param("tab", "recommended").param("limit", "50"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(before).get("feeds").valueStream().map(node -> node.get("id").asLong()).toList()).doesNotContain(feed);
        patchState(false, null).andExpect(status().isOk());
        String after = mvc.perform(asMember(get("/api/v1/feeds"), other).param("tab", "recommended").param("limit", "50"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(after).get("feeds").valueStream().map(node -> node.get("id").asLong()).toList()).contains(feed);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/likes", "/bookmarks", "/comments", "/comments/0/likes"})
    void hiddenParentRejectsAllNewInteractionsIncludingFromOwner(String suffix) throws Exception {
        patchState(true, "사유").andExpect(status().isOk());
        String path = "/api/v1/feeds/" + feed + suffix.replace("/0/", "/" + comment + "/");
        for (long member : List.of(owner, other)) {
            mvc.perform(asMember(post(path), member).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"새 댓글\"}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FEED_NOT_FOUND"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comments WHERE feed_id=?", Long.class, feed)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feed_likes WHERE feed_id=?", Long.class, feed)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feed_bookmarks WHERE feed_id=?", Long.class, feed)).isEqualTo(1);
    }

    @Test
    void restorationAllowsNewInteractionsAgainWithoutRecreatingExistingData() throws Exception {
        patchState(true, "사유").andExpect(status().isOk()); patchState(false, null).andExpect(status().isOk());
        mvc.perform(asMember(post("/api/v1/feeds/" + feed + "/likes"), other).with(csrf())).andExpect(status().isCreated());
        mvc.perform(asMember(post("/api/v1/feeds/" + feed + "/bookmarks"), other).with(csrf())).andExpect(status().isCreated());
        mvc.perform(asMember(post("/api/v1/feeds/" + feed + "/comments"), other).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"복구 후 댓글\"}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comments WHERE feed_id=?", Long.class, feed)).isEqualTo(3);
    }

    @Test
    void editingHiddenContentDoesNotUnhideItOrOverwriteReactionCounters() throws Exception {
        patchState(true, "사유").andExpect(status().isOk());
        feeds.updateFeed(feed, owner, new UpdateFeedCommand("수정 본문", Visibility.FOLLOWERS, List.of(101L), List.of(201L)));
        assertThat(hiddenAt()).isEqualTo(NOW);
        assertThat(jdbc.queryForObject("SELECT like_count FROM feeds WHERE id=?", Long.class, feed)).isEqualTo(1);
        assertThat(logs()).isEqualTo(1);
    }

    @Test
    void auditFailureRollsBackHideAndRestore() throws Exception {
        jdbc.execute("ALTER TABLE admin_action_logs ADD CONSTRAINT ck_s10_reject_reason CHECK (reason IS DISTINCT FROM 's10-rollback')");
        patchState(true, "s10-rollback").andExpect(status().isInternalServerError());
        assertThat(hiddenAt()).isNull(); assertThat(logs()).isZero();
        patchState(true, "정상").andExpect(status().isOk());
        patchState(false, "s10-rollback").andExpect(status().isInternalServerError());
        assertThat(hiddenAt()).isEqualTo(NOW); assertThat(logs()).isEqualTo(1);
    }

    @Test
    void internalInputPortRecordsCauseOnceAndOuterRollbackRemovesBothChanges() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(moderation.setHidden(feed, true, "신고 숨김", admin, report)).isTrue();
            status.setRollbackOnly();
        });
        assertThat(hiddenAt()).isNull(); assertThat(logs()).isZero();
        assertThat(moderation.setHidden(feed, true, "신고 숨김", admin, report)).isTrue();
        assertThat(moderation.setHidden(feed, true, "신고 숨김", admin, report)).isFalse();
        assertThat(logs()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT source_report_id FROM admin_action_logs WHERE target_id=?", Long.class, feed)).isEqualTo(report);
        assertReportUnchanged();
    }

    @Test
    void concurrentSameStateRequestsLogOnlyOneChange() throws Exception {
        race(() -> moderation.setHidden(publicId, true, "첫 관리자", admin), () -> moderation.setHidden(publicId, true, "다른 관리자", secondAdmin));
        assertThat(hiddenAt()).isEqualTo(NOW); assertThat(logs()).isEqualTo(1);
        race(() -> moderation.setHidden(publicId, false, null, admin), () -> moderation.setHidden(publicId, false, null, secondAdmin));
        assertThat(hiddenAt()).isNull(); assertThat(logs()).isEqualTo(2);
    }

    @Test
    void editingAndHidingSerializeOnSameFeedAndRefreshStaleManagedRow() throws Exception {
        race(() -> moderation.setHidden(publicId, true, "숨김", admin),
                () -> feeds.updateFeed(feed, owner, new UpdateFeedCommand("동시 수정", Visibility.PRIVATE, List.of(101L), List.of(201L))));
        assertThat(hiddenAt()).isEqualTo(NOW);
        assertThat(jdbc.queryForObject("SELECT content FROM feeds WHERE id=?", String.class, feed)).isEqualTo("동시 수정");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            repository.findById(feed);
            jdbc.update("UPDATE feeds SET hidden_at=NULL WHERE id=?", feed);
            assertThat(repository.findByIdForUpdate(feed).getHiddenAt()).isNull();
            status.setRollbackOnly();
        });
        assertThat(hiddenAt()).isEqualTo(NOW);
    }

    @Test
    void newReactionAndHidingSerializeWithoutLosingCountersOrAcceptingAfterHide() throws Exception {
        AtomicReference<String> result = new AtomicReference<>();
        race(() -> moderation.setHidden(publicId, true, "숨김", admin), () -> {
            try { reactions.likeFeed(feed, other); result.set("OK"); }
            catch (BusinessException error) { result.set(error.getErrorCode().name()); }
            return null;
        });
        assertThat(result.get()).isIn("OK", "FEED_NOT_FOUND");
        long expected = result.get().equals("OK") ? 2 : 1;
        assertThat(jdbc.queryForObject("SELECT like_count FROM feeds WHERE id=?", Long.class, feed)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feed_likes WHERE feed_id=?", Long.class, feed)).isEqualTo(expected);
        assertThat(hiddenAt()).isEqualTo(NOW);
        assertThat(logs()).isEqualTo(1);
    }

    @Test
    void csrfAndAdminAuthorizationRemainRequired() throws Exception {
        mvc.perform(asAdmin(patch("/api/v1/admin/feeds/" + publicId)).contentType(MediaType.APPLICATION_JSON).content(body(true, "사유")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
        mvc.perform(api(patch("/api/v1/admin/feeds/" + publicId)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(true, "사유")))
                .andExpect(status().isUnauthorized());
        mvc.perform(asMember(patch("/api/v1/admin/feeds/" + publicId), other).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(true, "사유")))
                .andExpect(status().isForbidden());
        assertThat(hiddenAt()).isNull(); assertThat(logs()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions patchState(boolean hidden, String reason) throws Exception {
        return mvc.perform(patchRequest().content(body(hidden, reason)));
    }
    private MockHttpServletRequestBuilder patchRequest() { return asAdmin(patch("/api/v1/admin/feeds/" + publicId)).with(csrf()).contentType(MediaType.APPLICATION_JSON); }
    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return api(request).with(authentication(UsernamePasswordAuthenticationToken.authenticated(new AdminPrincipal(admin), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))));
    }
    private MockHttpServletRequestBuilder asMember(MockHttpServletRequestBuilder request, long user) {
        return api(request).with(authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(user), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
    }
    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder request) { return request.servletPath("/api/v1"); }
    private static String body(boolean hidden, String reason) { var node=JSON.createObjectNode().put("hidden", hidden); if(reason!=null)node.put("reason", reason); return JSON.writeValueAsString(node); }
    private OffsetDateTime hiddenAt() { return jdbc.queryForObject("SELECT hidden_at FROM feeds WHERE id=?", OffsetDateTime.class, feed); }
    private long logs() { return jdbc.queryForObject("SELECT count(*) FROM admin_action_logs WHERE target_type='FEED' AND target_id=?", Long.class, feed); }
    private String immutableData() { return jdbc.queryForObject("SELECT (to_jsonb(f)-'hidden_at'-'updated_at')::text FROM feeds f WHERE id=?", String.class, feed); }
    private void assertReportUnchanged() {
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id=?", String.class, report)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT snapshot FROM reports WHERE id=?", String.class, report)).isEqualTo("original snapshot");
    }
    private long user() { return jdbc.queryForObject("INSERT INTO users(username) VALUES (?) RETURNING id", Long.class, "s10" + UUID.randomUUID().toString().replace("-", "").substring(0,12)); }
    private long admin() { return jdbc.queryForObject("INSERT INTO admins(username,password_hash,role,status) VALUES (?,'test-only','OPERATOR','ACTIVE') RETURNING id", Long.class, "s10-"+UUID.randomUUID()); }
    private static void race(Supplier<?> first, Supplier<?> second) throws Exception {
        try (var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);
            var a=pool.submit(() -> { start.await(); return first.get(); });
            var b=pool.submit(() -> { start.await(); return second.get(); });
            start.countDown(); a.get(20,TimeUnit.SECONDS); b.get(20,TimeUnit.SECONDS);
        }
    }
}
