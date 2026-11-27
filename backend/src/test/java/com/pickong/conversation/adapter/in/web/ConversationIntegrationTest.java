package com.pickong.conversation.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.LoginUser;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화방 HTTP API: 실제 보안 필터 · 마이그레이션(V21) · 조회 SQL을 함께 확인합니다. 테스트마다 롤백합니다.
 * 메시지 보내기 API가 없으므로(STOMP 이슈) 메시지는 JDBC로 직접 넣습니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ConversationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long me;
    private String myName;
    private long jimin;
    private String jiminName;
    private long minsu;
    private String minsuName;
    /** 넣는 메시지마다 1초씩 늦게: id 순서와 시각 순서를 같게, 참여 시각보다 뒤로 */
    private int messageSeq;

    @BeforeEach
    void setUp() {
        myName = randomUsername();
        me = insertUser(myName, null);
        jiminName = randomUsername();
        jimin = insertUser(jiminName, "지민");
        minsuName = randomUsername();
        minsu = insertUser(minsuName, null);
    }

    @Test
    @DisplayName("1:1 열기: 처음엔 201로 만들고, 상대가 반대로 열어도 같은 대화방을 200으로 (아이디는 대소문자 무시)")
    void openDirectCreatesOnce() throws Exception {
        String jiminPublicId = publicIdOf(jimin);

        String id = openId(me, jiminName, 201);
        open(me, jiminName.toUpperCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        open(jimin, myName)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        open(me, jiminName)
                .andExpect(jsonPath("$.partner.id").value(jiminPublicId))
                .andExpect(jsonPath("$.partner.username").value(jiminName))
                .andExpect(jsonPath("$.partner.nickname").value("지민"))
                .andExpect(jsonPath("$.partner.profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.lastMessage").value(nullValue()))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM conversations WHERE direct_key = ?", Integer.class,
                Math.min(me, jimin) + ":" + Math.max(me, jimin));
        org.assertj.core.api.Assertions.assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("1:1 열기 실패: 나 자신 400, 없는 아이디 · 정지 회원 404, 빈 아이디 400")
    void openDirectFailures() throws Exception {
        open(me, myName)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_CONVERSATION_NOT_ALLOWED"));
        open(me, "nobody_here")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", minsu);
        open(me, minsuName)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        open(me, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("목록: 메시지가 있는 내 대화방만, 마지막 메시지가 최근인 순서, 프론트 ConversationDto 모양")
    void listShape() throws Exception {
        long withJimin = Long.parseLong(openId(me, jiminName, 201));
        long withMinsu = Long.parseLong(openId(me, minsuName, 201));
        openId(jimin, minsuName, 201); // 남의 대화방
        insertMessage(withJimin, jimin, "안녕");
        long mine = insertMessage(withJimin, me, "반가워");
        insertMessage(withMinsu, minsu, "점심?");

        list(me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", contains(String.valueOf(withMinsu), String.valueOf(withJimin))))
                .andExpect(jsonPath("$[0].unreadCount").value(1))
                .andExpect(jsonPath("$[0].lastMessage.text").value("점심?"))
                .andExpect(jsonPath("$[0].lastMessage.isMine").value(false))
                .andExpect(jsonPath("$[1].unreadCount").value(1))
                .andExpect(jsonPath("$[1].lastMessage.id").value(String.valueOf(mine)))
                .andExpect(jsonPath("$[1].lastMessage.conversationId").value(String.valueOf(withJimin)))
                .andExpect(jsonPath("$[1].lastMessage.senderId").value(publicIdOf(me)))
                .andExpect(jsonPath("$[1].lastMessage.isMine").value(true))
                .andExpect(jsonPath("$[1].lastMessage.text").value("반가워"))
                .andExpect(jsonPath("$[1].lastMessage.attachment").value(nullValue()))
                .andExpect(jsonPath("$[1].updatedAt").isNotEmpty());
    }

    @Test
    @DisplayName("메시지가 없는 대화방은 목록에 없지만 상세로는 볼 수 있다")
    void emptyConversationOnlyInDetail() throws Exception {
        String id = openId(me, jiminName, 201);

        list(me).andExpect(jsonPath("$", hasSize(0)));
        detail(me, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.lastMessage").value(nullValue()));
    }

    @Test
    @DisplayName("참여하지 않은 대화방은 상세 · 내역 · 읽음 · 나가기 모두 404 CONVERSATION_NOT_FOUND")
    void nonMemberSeesNothing() throws Exception {
        String id = openId(jimin, minsuName, 201);

        detail(me, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
        messages(me, id, "").andExpect(status().isNotFound());
        read(me, id, null).andExpect(status().isNotFound());
        leave(me, id).andExpect(status().isNotFound());
        detail(me, "987654321").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("내역: 최신 size개를 오래된 순으로, cursor로 이전 내역, after로 새 메시지")
    void messagePaging() throws Exception {
        String id = openId(me, jiminName, 201);
        long conversation = Long.parseLong(id);
        long m1 = insertMessage(conversation, me, "1");
        long m2 = insertMessage(conversation, jimin, "2");
        long m3 = insertMessage(conversation, me, "3");
        long m4 = insertMessage(conversation, jimin, "4");
        long m5 = insertMessage(conversation, jimin, "5");

        messages(me, id, "size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].text", contains("4", "5")))
                .andExpect(jsonPath("$.nextCursor").value(String.valueOf(m4)));
        messages(me, id, "size=2&cursor=" + m4)
                .andExpect(jsonPath("$.items[*].text", contains("2", "3")))
                .andExpect(jsonPath("$.nextCursor").value(String.valueOf(m2)));
        messages(me, id, "size=2&cursor=" + m2)
                .andExpect(jsonPath("$.items[*].id", contains(String.valueOf(m1))))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        messages(me, id, "size=2&after=" + m1)
                .andExpect(jsonPath("$.items[*].text", contains("2", "3")))
                .andExpect(jsonPath("$.nextCursor").value(String.valueOf(m3)));
        messages(me, id, "size=2&after=" + m3)
                .andExpect(jsonPath("$.items[*].text", contains("4", "5")))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        messages(me, id, "after=" + m5)
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    @DisplayName("내역 입력 오류: cursor와 after를 함께, size 범위 밖은 400 INVALID_INPUT")
    void messagePagingInvalid() throws Exception {
        String id = openId(me, jiminName, 201);

        messages(me, id, "cursor=5&after=3")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        messages(me, id, "size=0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        messages(me, id, "size=101").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("지운 메시지는 내역 · 마지막 메시지 · 안 읽은 수에서 빠진다")
    void deletedMessagesAreHidden() throws Exception {
        String id = openId(me, jiminName, 201);
        long conversation = Long.parseLong(id);
        insertMessage(conversation, jimin, "남음");
        long deleted = insertMessage(conversation, jimin, "지움");
        jdbc.update("UPDATE messages SET deleted_at = now() WHERE id = ?", deleted);

        messages(me, id, "").andExpect(jsonPath("$.items[*].text", contains("남음")));
        detail(me, id)
                .andExpect(jsonPath("$.lastMessage.text").value("남음"))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    @DisplayName("읽음: 보낸 위치까지, 뒤로 가지 않음, 본문이 없으면 마지막 메시지까지. 조회만으로는 읽지 않음")
    void markRead() throws Exception {
        String withJimin = openId(me, jiminName, 201);
        String withMinsu = openId(me, minsuName, 201);
        long j1 = insertMessage(Long.parseLong(withJimin), jimin, "j1");
        long j2 = insertMessage(Long.parseLong(withJimin), jimin, "j2");
        insertMessage(Long.parseLong(withJimin), me, "내 메시지는 안 읽은 수에 안 셈");
        insertMessage(Long.parseLong(withMinsu), minsu, "m1");

        unread(me).andExpect(jsonPath("$.count").value(3));
        messages(me, withJimin, "");
        unread(me).andExpect(jsonPath("$.count").value(3));

        read(me, withJimin, String.valueOf(j1)).andExpect(status().isNoContent());
        detail(me, withJimin).andExpect(jsonPath("$.unreadCount").value(1));

        read(me, withJimin, String.valueOf(j2)).andExpect(status().isNoContent());
        read(me, withJimin, String.valueOf(j1)).andExpect(status().isNoContent());
        detail(me, withJimin).andExpect(jsonPath("$.unreadCount").value(0));
        Long stored = jdbc.queryForObject(
                "SELECT last_read_message_id FROM conversation_members WHERE conversation_id = ? AND user_id = ?",
                Long.class, Long.parseLong(withJimin), me);
        org.assertj.core.api.Assertions.assertThat(stored).isEqualTo(j2);

        read(me, withMinsu, null).andExpect(status().isNoContent());
        unread(me).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("읽음 입력 오류: 다른 대화방의 메시지는 404 MESSAGE_NOT_FOUND, 숫자가 아니면 400")
    void markReadInvalid() throws Exception {
        String withJimin = openId(me, jiminName, 201);
        String withMinsu = openId(me, minsuName, 201);
        long other = insertMessage(Long.parseLong(withMinsu), minsu, "다른 대화방");

        read(me, withJimin, String.valueOf(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
        read(me, withJimin, "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("나가기: 목록 · 상세에서 사라지고, 다시 나가면 404. 상대에게는 그대로 보임")
    void leave() throws Exception {
        String id = openId(me, jiminName, 201);
        insertMessage(Long.parseLong(id), jimin, "안녕");

        leave(me, id).andExpect(status().isNoContent());

        list(me).andExpect(jsonPath("$", hasSize(0)));
        detail(me, id).andExpect(status().isNotFound());
        unread(me).andExpect(jsonPath("$.count").value(0));
        leave(me, id).andExpect(status().isNotFound());
        list(jimin).andExpect(jsonPath("$[*].id", contains(id)));
    }

    @Test
    @DisplayName("나갔던 대화방을 다시 열면 같은 대화방에 다시 참여하고, 다시 참여한 뒤의 메시지만 보인다")
    void rejoinHidesOldMessages() throws Exception {
        String id = openId(me, jiminName, 201);
        long conversation = Long.parseLong(id);
        insertMessage(conversation, jimin, "예전 메시지");
        leave(me, id).andExpect(status().isNoContent());
        // 대화를 어제 시작한 것으로 옮겨, 예전 메시지를 다시 참여한 시각(지금)보다 확실히 앞에 둡니다.
        jdbc.update("UPDATE messages SET created_at = created_at - interval '1 day' WHERE conversation_id = ?", conversation);
        jdbc.update("UPDATE conversation_members SET joined_at = joined_at - interval '1 day' WHERE conversation_id = ?",
                conversation);

        open(me, jiminName).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        detail(me, id)
                .andExpect(jsonPath("$.lastMessage").value(nullValue()))
                .andExpect(jsonPath("$.unreadCount").value(0));
        messages(me, id, "").andExpect(jsonPath("$.items", hasSize(0)));

        insertMessage(conversation, jimin, "새 메시지");
        messages(me, id, "").andExpect(jsonPath("$.items[*].text", contains("새 메시지")));
        messages(jimin, id, "").andExpect(jsonPath("$.items[*].text", contains("예전 메시지", "새 메시지")));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/v1/conversations").servletPath("/api/v1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/conversations/unread-count").servletPath("/api/v1")).andExpect(status().isUnauthorized());
    }

    // ---- 요청 ----

    private ResultActions open(long userId, String username) throws Exception {
        return mvc.perform(post("/api/v1/conversations/direct").servletPath("/api/v1")
                .with(authentication(member(userId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\"}"));
    }

    private String openId(long userId, String username, int expectedStatus) throws Exception {
        String body = open(userId, username)
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions list(long userId) throws Exception {
        return mvc.perform(get("/api/v1/conversations").servletPath("/api/v1").with(authentication(member(userId))));
    }

    private ResultActions unread(long userId) throws Exception {
        return mvc.perform(get("/api/v1/conversations/unread-count").servletPath("/api/v1")
                .with(authentication(member(userId))));
    }

    private ResultActions detail(long userId, String id) throws Exception {
        return mvc.perform(get("/api/v1/conversations/" + id).servletPath("/api/v1").with(authentication(member(userId))));
    }

    private ResultActions messages(long userId, String id, String query) throws Exception {
        return mvc.perform(get("/api/v1/conversations/" + id + "/messages?" + query).servletPath("/api/v1")
                .with(authentication(member(userId))));
    }

    private ResultActions read(long userId, String id, String lastReadMessageId) throws Exception {
        var request = patch("/api/v1/conversations/" + id + "/read").servletPath("/api/v1")
                .with(authentication(member(userId))).with(csrf());
        if (lastReadMessageId != null) {
            request.contentType(MediaType.APPLICATION_JSON).content("{\"lastReadMessageId\":\"" + lastReadMessageId + "\"}");
        }
        return mvc.perform(request);
    }

    private ResultActions leave(long userId, String id) throws Exception {
        return mvc.perform(delete("/api/v1/conversations/" + id + "/members/me").servletPath("/api/v1")
                .with(authentication(member(userId))).with(csrf()));
    }

    // ---- 데이터 ----

    private static String randomUsername() {
        return "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private long insertUser(String username, String displayName) {
        long id = jdbc.queryForObject("INSERT INTO users (username, email, display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, username, username + "@example.com", displayName);
        // 필수 약관 재동의 필터에 걸리지 않도록 동의해 둡니다.
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, now() FROM terms WHERE is_required = TRUE
                """, id);
        return id;
    }

    private String publicIdOf(long userId) {
        return jdbc.queryForObject("SELECT CAST(public_id AS TEXT) FROM users WHERE id = ?", String.class, userId);
    }

    private long insertMessage(long conversationId, long senderId, String content) {
        OffsetDateTime createdAt = OffsetDateTime.now().plusSeconds(++messageSeq);
        return jdbc.queryForObject("""
                INSERT INTO messages (conversation_id, sender_id, client_message_id, content, created_at)
                VALUES (?, ?, gen_random_uuid(), ?, ?) RETURNING id
                """, Long.class, conversationId, senderId, content, createdAt);
    }

    private static UsernamePasswordAuthenticationToken member(long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
