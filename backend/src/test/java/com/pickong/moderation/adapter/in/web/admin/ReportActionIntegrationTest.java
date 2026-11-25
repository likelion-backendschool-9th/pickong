package com.pickong.moderation.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.common.security.LoginUser;
import com.pickong.common.error.BusinessException;
import com.pickong.feed.application.dto.UpdateCommentCommand;
import com.pickong.feed.application.port.in.CommentUseCase;
import com.pickong.feed.application.port.in.FeedModerationUseCase;
import com.pickong.moderation.application.dto.SanctionCommand;
import com.pickong.moderation.application.port.in.ReportAdminUseCase;
import com.pickong.moderation.application.port.in.SanctionUseCase;
import com.pickong.moderation.domain.model.Sanction;
import com.pickong.moderation.domain.vo.SanctionType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 신고 잠금 → 실제 업무 → 선택 신고 처리 → 로그의 원자성과 AFTER_COMMIT 토큰 폐기를 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportActionIntegrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T03:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReportAdminUseCase reports;
    @Autowired SanctionUseCase sanctions;
    @Autowired FeedModerationUseCase contents;
    @Autowired CommentUseCase comments;
    @MockitoBean Clock clock;
    private long owner, commenter, reporter, siblingReporter, admin, otherAdmin, feed, comment, primary, sibling;
    private String snapshot;

    @BeforeEach
    void fixtures() {
        given(clock.instant()).willReturn(NOW.toInstant());
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
        owner = user(); commenter = user(); reporter = user(); siblingReporter = user();
        admin = admin(); otherAdmin = admin();
        for (long id : List.of(owner, commenter, reporter, siblingReporter)) {
            jdbc.update("INSERT INTO user_terms_agreements(user_id,terms_id,agreed_at) SELECT ?,id,? FROM terms WHERE is_required=true", id, NOW);
        }
        feed = jdbc.queryForObject("INSERT INTO feeds(user_id,content,visibility) VALUES (?,'현재 게시물','PUBLIC') RETURNING id", Long.class, owner);
        comment = jdbc.queryForObject("INSERT INTO comments(feed_id,user_id,content,like_count) VALUES (?,?,'현재 댓글',1) RETURNING id", Long.class, feed, commenter);
        jdbc.update("INSERT INTO comment_likes(comment_id,user_id) VALUES (?,?)", comment, reporter);
        snapshot = snapshot(owner);
        primary = report(reporter, "FEED", feed, snapshot);
        sibling = report(siblingReporter, "FEED", feed, snapshot);
        for (long id : List.of(owner, commenter)) {
            for (int i=0;i<2;i++) jdbc.update("INSERT INTO refresh_tokens(user_id,token_hash,family,expires_at) VALUES (?,?,?,?)",
                    id, "s11-"+UUID.randomUUID(), "family-"+i, NOW.plusDays(60));
        }
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("ALTER TABLE reports DROP CONSTRAINT IF EXISTS ck_s11_reject_result");
        jdbc.execute("ALTER TABLE admin_action_logs DROP CONSTRAINT IF EXISTS ck_s11_reject_log");
        jdbc.update("DELETE FROM admin_action_logs WHERE admin_id IN (?,?)", admin, otherAdmin);
        jdbc.update("DELETE FROM user_sanctions WHERE user_id IN (?,?)", owner, commenter);
        jdbc.update("DELETE FROM reports WHERE reporter_id IN (?,?)", reporter, siblingReporter);
        jdbc.update("DELETE FROM feeds WHERE id=?", feed);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (?,?)", owner, commenter);
        jdbc.update("DELETE FROM user_terms_agreements WHERE user_id IN (?,?,?,?)", owner, commenter, reporter, siblingReporter);
        jdbc.update("DELETE FROM users WHERE id IN (?,?,?,?)", owner, commenter, reporter, siblingReporter);
        jdbc.update("DELETE FROM admins WHERE id IN (?,?)", admin, otherAdmin);
    }

    @ParameterizedTest
    @ValueSource(strings={"WARNING","SUSPEND_7","SUSPEND_30","BAN"})
    void sanctionUsesCommonRulesAndResolvesOnlySelectedReport(String type) throws Exception {
        mvc.perform(resolve(primary,"SANCTION_USER","  처리 사유  ",type)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution.action").value("SANCTION_USER"))
                .andExpect(jsonPath("$.resolution.memo").value("처리 사유"))
                .andExpect(jsonPath("$.reportedUser.id").value(Long.toString(owner)));
        assertSelected(primary, "SANCTION_USER");
        assertThat(state(sibling)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT snapshot FROM reports WHERE id=?",String.class,primary)).isEqualTo(snapshot);
        assertThat(jdbc.queryForObject("SELECT source_report_id FROM user_sanctions WHERE user_id=?",Long.class,owner)).isEqualTo(primary);
        assertThat(logs()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT action_type FROM admin_action_logs WHERE source_report_id=?",String.class,primary)).isEqualTo("SANCTION_USER");
        assertThat(tokens(owner)).isEqualTo(type.equals("WARNING")?2:0);
        assertThat(tokens(commenter)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=?",String.class,owner))
                .isEqualTo(type.equals("WARNING")?"ACTIVE":type.equals("BAN")?"BANNED":"SUSPENDED");
        if(type.startsWith("SUSPEND")) assertThat(jdbc.queryForObject("SELECT suspended_until FROM users WHERE id=?",OffsetDateTime.class,owner))
                .isEqualTo(NOW.plusHours(type.equals("SUSPEND_7")?168:720));
        mvc.perform(resolve(primary,"SANCTION_USER","재처리",type)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_RESOLVED"));
        assertThat(logs()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings={"FEED","COMMENT","USER"})
    void targetsActualAuthorAndIgnoresSpoofedRequestAndSnapshotWhenRowExists(String target) throws Exception {
        long actual = target.equals("COMMENT")?commenter:owner;
        long id = target.equals("FEED") ? primary : report(reporter,target,target.equals("COMMENT")?comment:owner,snapshot(reporter));
        if(target.equals("FEED")) { id=primary; jdbc.update("UPDATE reports SET snapshot=? WHERE id=?",snapshot(reporter),id); }
        mvc.perform(adminRequest(post("/api/v1/admin/reports/"+id+"/resolve")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SANCTION_USER\",\"memo\":\"경고\",\"sanctionType\":\"WARNING\",\"userId\":"+reporter+",\"adminId\":"+otherAdmin+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reportedUser.id").value(Long.toString(actual)));
        assertThat(jdbc.queryForObject("SELECT user_id FROM user_sanctions WHERE source_report_id=?",Long.class,id)).isEqualTo(actual);
        assertThat(jdbc.queryForObject("SELECT admin_id FROM user_sanctions WHERE source_report_id=?",Long.class,id)).isEqualTo(admin);
    }

    @Test
    void missingContentCanUseValidStoredSnapshotAuthor() throws Exception {
        jdbc.update("DELETE FROM feeds WHERE id=?",feed);
        mvc.perform(resolve(primary,"SANCTION_USER","저장된 증거","BAN")).andExpect(status().isOk());
        assertSelected(primary,"SANCTION_USER");
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=?",String.class,owner)).isEqualTo("BANNED");
        assertThat(tokens(owner)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings={"legacy text","{broken","{\"reportedUserId\":0}","{\"reportedUserId\":true}","{\"reportedUserId\":1.5}","{\"reportedUserId\":\"9223372036854775808\"}"})
    void missingAuthorIsNotGuessedFromTargetOrReporter(String stored) throws Exception {
        jdbc.update("DELETE FROM feeds WHERE id=?",feed);
        jdbc.update("UPDATE reports SET snapshot=? WHERE id=?",stored,primary);
        mvc.perform(resolve(primary,"SANCTION_USER","확인","WARNING")).andExpect(status().isNotFound());
        assertThat(state(primary)).isEqualTo("PENDING"); assertThat(logs()).isZero(); assertThat(sanctionCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings={"FEED","COMMENT"})
    void hideResolvesSelectedReportAndLogsExactlyOnceWithoutReplacingSnapshot(String target) throws Exception {
        long id = target.equals("FEED")?primary:report(reporter,"COMMENT",comment,snapshot(commenter));
        String original=jdbc.queryForObject("SELECT snapshot FROM reports WHERE id=?",String.class,id);
        mvc.perform(resolve(id,"HIDE_CONTENT","숨김",null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.target.visibility").value("HIDDEN"))
                .andExpect(jsonPath("$.target.link").value(org.hamcrest.Matchers.nullValue()));
        assertSelected(id,"HIDE_CONTENT");
        assertThat(jdbc.queryForObject("SELECT hidden_at FROM "+(target.equals("FEED")?"feeds":"comments")+" WHERE id=?",OffsetDateTime.class,target.equals("FEED")?feed:comment)).isEqualTo(NOW);
        assertThat(jdbc.queryForObject("SELECT snapshot FROM reports WHERE id=?",String.class,id)).isEqualTo(original);
        assertThat(state(sibling)).isEqualTo("PENDING"); assertThat(logs()).isEqualTo(1); assertThat(sanctionCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT target_type FROM admin_action_logs WHERE source_report_id=?",String.class,id)).isEqualTo(target);
        assertThat(jdbc.queryForObject("SELECT content FROM comments WHERE id=?",String.class,comment)).isEqualTo("현재 댓글");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comment_likes WHERE comment_id=?",Long.class,comment)).isEqualTo(1);
    }

    @Test
    void alreadyHiddenTargetCanCloseOnlySelectedReportWithoutDuplicateContentLog() throws Exception {
        contents.setHidden(feed,true,"직접 숨김",admin,null);
        assertThat(state(primary)).isEqualTo("PENDING");
        mvc.perform(resolve(primary,"HIDE_CONTENT","신고 확인",null)).andExpect(status().isOk());
        assertThat(logs()).isEqualTo(1); assertThat(state(sibling)).isEqualTo("PENDING");
        assertSelected(primary,"HIDE_CONTENT");
    }

    @ParameterizedTest
    @ValueSource(strings={"FEED","COMMENT"})
    void deletedTargetsCannotBeNewlyHidden(String target) throws Exception {
        long id=target.equals("FEED")?primary:report(reporter,"COMMENT",comment,snapshot(commenter));
        jdbc.update("UPDATE "+(target.equals("FEED")?"feeds":"comments")+" SET deleted_at=? WHERE id=?",NOW,target.equals("FEED")?feed:comment);
        mvc.perform(resolve(id,"HIDE_CONTENT","삭제 대상",null)).andExpect(status().isNotFound());
        assertThat(state(id)).isEqualTo("PENDING"); assertThat(logs()).isZero();
    }

    @Test
    void duplicateCauseAndActiveRestrictionConflictsLeaveReportPending() throws Exception {
        sanctions.sanction(SanctionCommand.fromAdminType(owner,"WARNING","선행",admin,primary));
        mvc.perform(resolve(primary,"SANCTION_USER","중복","WARNING")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SANCTION_SOURCE"));
        assertThat(state(primary)).isEqualTo("PENDING"); assertThat(sanctionCount()).isEqualTo(1);
    }

    @Test
    void banUpgradeFromDirectSuspensionPreservesOldHistoryAndDoesNotCloseOtherReports() throws Exception {
        Sanction old=sanctions.sanction(SanctionCommand.fromAdminType(owner,"SUSPEND_7","직접 정지",admin,null));
        mvc.perform(resolve(primary,"SANCTION_USER","기간 연장 시도","SUSPEND_30")).andExpect(status().isConflict());
        assertThat(state(primary)).isEqualTo("PENDING");
        mvc.perform(resolve(primary,"SANCTION_USER","영구 전환","BAN")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT end_reason FROM user_sanctions WHERE id=?",String.class,old.getId())).isEqualTo("SUPERSEDED_BY_BAN");
        assertThat(state(sibling)).isEqualTo("PENDING"); assertThat(logs()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings={"SANCTION_USER","HIDE_CONTENT"})
    void collectionWorkRemainsDeferred(String action) throws Exception {
        long id=report(reporter,"COLLECTION",Long.MAX_VALUE,snapshot(owner));
        mvc.perform(resolve(id,action,"보류",action.equals("SANCTION_USER")?"BAN":null)).andExpect(status().isNotImplemented());
        assertThat(state(id)).isEqualTo("PENDING"); assertThat(logs()).isZero();
    }

    @Test
    void userCannotBeHiddenAndDismissalHasSingleReportAudit() throws Exception {
        long id=report(reporter,"USER",owner,snapshot(owner));
        mvc.perform(resolve(id,"HIDE_CONTENT","잘못된 액션",null)).andExpect(status().isBadRequest());
        mvc.perform(resolve(id,"DISMISS",null,null)).andExpect(status().isOk());
        assertSelected(id,"DISMISS"); assertThat(logs()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT action_type FROM admin_action_logs WHERE source_report_id=?",String.class,id)).isEqualTo("DISMISS_REPORT");
    }

    @ParameterizedTest
    @ValueSource(strings={"SANCTION_USER","HIDE_FEED","HIDE_COMMENT","DISMISS"})
    void reportResultFailureRollsBackAllBusinessWrites(String operation) throws Exception {
        jdbc.execute("ALTER TABLE reports ADD CONSTRAINT ck_s11_reject_result CHECK (resolution_memo IS DISTINCT FROM 's11-reject')");
        failedAtomicOperation(operation,"s11-reject");
    }

    @ParameterizedTest
    @ValueSource(strings={"SANCTION_USER","HIDE_FEED","HIDE_COMMENT","DISMISS"})
    void auditFailureRollsBackReportAndBusinessWrites(String operation) throws Exception {
        jdbc.execute("ALTER TABLE admin_action_logs ADD CONSTRAINT ck_s11_reject_log CHECK (reason IS DISTINCT FROM 's11-reject')");
        failedAtomicOperation(operation,"s11-reject");
    }

    private void failedAtomicOperation(String operation,String memo) throws Exception {
        long id=operation.equals("HIDE_COMMENT")?report(reporter,"COMMENT",comment,snapshot(commenter)):primary;
        String action=operation.startsWith("HIDE")?"HIDE_CONTENT":operation;
        mvc.perform(resolve(id,action,memo,action.equals("SANCTION_USER")?"BAN":null)).andExpect(status().isInternalServerError());
        assertThat(state(id)).isEqualTo("PENDING"); assertThat(logs()).isZero(); assertThat(sanctionCount()).isZero();
        assertThat(tokens(owner)).isEqualTo(2); assertThat(tokens(commenter)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT hidden_at FROM feeds WHERE id=?",OffsetDateTime.class,feed)).isNull();
        assertThat(jdbc.queryForObject("SELECT hidden_at FROM comments WHERE id=?",OffsetDateTime.class,comment)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=?",String.class,owner)).isEqualTo("ACTIVE");
    }

    @Test
    void concurrentDifferentActionsOnSameReportHaveOneWinner() throws Exception {
        List<String> result=race(() -> outcome(() -> reports.resolve(primary,admin,"SANCTION_USER","경고","WARNING")),
                () -> outcome(() -> reports.resolve(primary,otherAdmin,"HIDE_CONTENT","숨김",null)));
        assertThat(result).containsExactlyInAnyOrder("OK","ALREADY_RESOLVED"); assertThat(logs()).isEqualTo(1);
        assertThat(state(sibling)).isEqualTo("PENDING");
    }

    @Test
    void differentReportsForSameUserSerializeWithoutCreatingCauseDuplicates() throws Exception {
        assertThat(race(() -> outcome(() -> reports.resolve(primary,admin,"SANCTION_USER","경고1","WARNING")),
                () -> outcome(() -> reports.resolve(sibling,otherAdmin,"SANCTION_USER","경고2","WARNING"))))
                .containsExactly("OK","OK");
        assertThat(sanctionCount()).isEqualTo(2); assertThat(logs()).isEqualTo(2);
    }

    @Test
    void hiddenCommentIsVisibleOnlyToItsAuthorAndAdminAndCannotReceiveRepliesOrLikes() throws Exception {
        long id=report(reporter,"COMMENT",comment,snapshot(commenter));
        mvc.perform(resolve(id,"HIDE_CONTENT","숨김",null)).andExpect(status().isOk());
        String path="/api/v1/feeds/"+feed+"/comments";
        mvc.perform(member(get(path),owner).param("limit","20")).andExpect(status().isOk()).andExpect(jsonPath("$.comments").isEmpty());
        mvc.perform(member(get(path),commenter).param("limit","20")).andExpect(status().isOk()).andExpect(jsonPath("$.comments.length()").value(1));
        mvc.perform(adminRequest(get(path)).param("limit","20")).andExpect(status().isOk()).andExpect(jsonPath("$.comments.length()").value(1));
        mvc.perform(member(get(path+"/"+comment+"/replies"),owner).param("limit","20")).andExpect(status().isNotFound());
        mvc.perform(member(post(path),owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"새 답글\",\"parentId\":"+comment+"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMENT_HIDDEN"));
        mvc.perform(member(post(path+"/"+comment+"/likes"),owner).with(csrf())).andExpect(status().isBadRequest());
        comments.updateComment(feed,comment,commenter,new UpdateCommentCommand("작성자 수정"));
        assertThat(jdbc.queryForObject("SELECT hidden_at FROM comments WHERE id=?",OffsetDateTime.class,comment)).isEqualTo(NOW);
        assertThat(jdbc.queryForObject("SELECT like_count FROM comments WHERE id=?",Long.class,comment)).isEqualTo(1);
    }

    @Test
    void hiddenReplyIsFilteredFromReplyCountsAndListsForOtherViewers() throws Exception {
        long reply=jdbc.queryForObject("INSERT INTO comments(feed_id,user_id,parent_id,content) VALUES (?,?,?,'대댓글') RETURNING id",Long.class,feed,owner,comment);
        long id=report(reporter,"COMMENT",reply,snapshot(owner));
        mvc.perform(resolve(id,"HIDE_CONTENT","대댓글 숨김",null)).andExpect(status().isOk());
        String path="/api/v1/feeds/"+feed+"/comments";
        mvc.perform(member(get(path),commenter).param("limit","20")).andExpect(status().isOk()).andExpect(jsonPath("$.comments[0].replyCount").value(0));
        mvc.perform(member(get(path+"/"+comment+"/replies"),commenter).param("limit","20")).andExpect(status().isOk()).andExpect(jsonPath("$.comments").isEmpty());
        mvc.perform(member(get(path+"/"+comment+"/replies"),owner).param("limit","20")).andExpect(status().isOk()).andExpect(jsonPath("$.comments.length()").value(1));
    }

    @Test
    void moderationAndAuthorEditUseSameCommentLockWithoutLosingHiddenState() throws Exception {
        long id=report(reporter,"COMMENT",comment,snapshot(commenter));
        race(() -> reports.resolve(id,admin,"HIDE_CONTENT","숨김",null), () -> comments.updateComment(feed,comment,commenter,new UpdateCommentCommand("동시 수정")));
        assertThat(jdbc.queryForObject("SELECT hidden_at FROM comments WHERE id=?",OffsetDateTime.class,comment)).isEqualTo(NOW);
        assertThat(jdbc.queryForObject("SELECT content FROM comments WHERE id=?",String.class,comment)).isEqualTo("동시 수정");
    }

    @Test
    void securityAndMalformedBodyKeepExistingContract() throws Exception {
        String path="/api/v1/admin/reports/"+primary+"/resolve";
        mvc.perform(adminRequest(post(path)).contentType(MediaType.APPLICATION_JSON).content(body("HIDE_CONTENT","숨김",null)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
        mvc.perform(api(post(path)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("HIDE_CONTENT","숨김",null))).andExpect(status().isUnauthorized());
        mvc.perform(member(post(path),owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("HIDE_CONTENT","숨김",null))).andExpect(status().isForbidden());
        mvc.perform(adminRequest(post(path)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(resolve(primary,"HIDE_CONTENT","\u00a0\u2003",null)).andExpect(status().isBadRequest());
        assertThat(state(primary)).isEqualTo("PENDING"); assertThat(logs()).isZero();
    }

    private MockHttpServletRequestBuilder resolve(long id,String action,String memo,String type) {
        return adminRequest(post("/api/v1/admin/reports/"+id+"/resolve")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(action,memo,type));
    }
    private static String body(String action,String memo,String type) {var n=JSON.createObjectNode().put("action",action);if(memo!=null)n.put("memo",memo);if(type!=null)n.put("sanctionType",type);return JSON.writeValueAsString(n);}
    private MockHttpServletRequestBuilder adminRequest(MockHttpServletRequestBuilder r) {return api(r).with(authentication(UsernamePasswordAuthenticationToken.authenticated(new AdminPrincipal(admin),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))));}
    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder r,long user) {return api(r).with(authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(user),null,List.of(new SimpleGrantedAuthority("ROLE_USER")))));}
    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder r) {return r.servletPath("/api/v1");}
    private long user() {return jdbc.queryForObject("INSERT INTO users(username) VALUES (?) RETURNING id",Long.class,"s11"+UUID.randomUUID().toString().replace("-","").substring(0,12));}
    private long admin() {return jdbc.queryForObject("INSERT INTO admins(username,password_hash,role,status) VALUES (?,'test-only','OPERATOR','ACTIVE') RETURNING id",Long.class,"s11-"+UUID.randomUUID());}
    private long report(long who,String target,long targetId,String stored) {return jdbc.queryForObject("INSERT INTO reports(reporter_id,target_type,target_id,reason,snapshot) VALUES (?,?,?,'SPAM',?) RETURNING id",Long.class,who,target,targetId,stored);}
    private static String snapshot(long author) {return "{\"text\":\"신고 당시 전체 본문\",\"imageUrl\":null,\"reportedUserId\":"+author+",\"textScope\":\"FULL\"}";}
    private String state(long id) {return jdbc.queryForObject("SELECT status FROM reports WHERE id=?",String.class,id);}
    private long logs() {return jdbc.queryForObject("SELECT count(*) FROM admin_action_logs WHERE admin_id IN (?,?)",Long.class,admin,otherAdmin);}
    private long sanctionCount() {return jdbc.queryForObject("SELECT count(*) FROM user_sanctions WHERE user_id IN (?,?)",Long.class,owner,commenter);}
    private long tokens(long id) {return jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id=? AND revoked_at IS NULL",Long.class,id);}
    private void assertSelected(long id,String action) {assertThat(state(id)).isEqualTo("RESOLVED");assertThat(jdbc.queryForObject("SELECT resolution_action FROM reports WHERE id=?",String.class,id)).isEqualTo(action);assertThat(jdbc.queryForObject("SELECT handled_by FROM reports WHERE id=?",Long.class,id)).isEqualTo(admin);}
    private static String outcome(Supplier<?> work) {try{work.get();return "OK";}catch(BusinessException ex){return ex.getErrorCode().name();}}
    private static <T> List<T> race(Supplier<T> first,Supplier<T> second) throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)){CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);var a=pool.submit(()->{ready.countDown();start.await();return first.get();});var b=pool.submit(()->{ready.countDown();start.await();return second.get();});assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));}
    }
}
