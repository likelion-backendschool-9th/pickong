package com.pickong.conversation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * 그룹 채팅 HTTP API (V24): 만들기 · 초대 · 이름 변경 · 알림 끄기 · 나가기, 안내 메시지, 목록 통합, 이전 대화 보기.
 * 테스트마다 롤백합니다. 글은 STOMP로만 보내므로 JDBC로 직접 넣습니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GroupConversationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long me;
    private final List<Long> others = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private int messageSeq;

    @BeforeEach
    void setUp() {
        String myName = randomUsername();
        me = insertUser(myName);
        names.add(myName);
        for (int i = 0; i < 10; i++) {
            String name = randomUsername();
            others.add(insertUser(name));
            names.add(name);
        }
    }

    /** names[0]은 나, names[1..10]은 다른 회원 */
    private String name(int index) {
        return names.get(index);
    }

    @Test
    @DisplayName("만들기: 201, GROUP · 이름 · 참여자(나 포함, isMe) · '만들기' 안내 메시지가 마지막 메시지")
    void create() throws Exception {
        String body = createGroup(me, " 성수 투어 ", name(1), name(2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("GROUP"))
                .andExpect(jsonPath("$.title").value("성수 투어"))
                .andExpect(jsonPath("$.partner").value(nullValue()))
                .andExpect(jsonPath("$.memberCount").value(3))
                .andExpect(jsonPath("$.members[*].username", contains(name(0), name(1), name(2))))
                .andExpect(jsonPath("$.members[0].isMe").value(true))
                .andExpect(jsonPath("$.muted").value(false))
                .andExpect(jsonPath("$.lastMessage.type").value("SYSTEM"))
                .andExpect(jsonPath("$.lastMessage.text").value(nullValue()))
                .andExpect(jsonPath("$.lastMessage.system.event").value("CREATED"))
                .andExpect(jsonPath("$.lastMessage.system.actor.username").value(name(0)))
                .andExpect(jsonPath("$.lastMessage.system.targets[*].username", contains(name(1), name(2))))
                .andExpect(jsonPath("$.lastMessage.system.title").value("성수 투어"))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        // 초대된 사람도 목록에서 바로 보이고, 안내 메시지는 안 읽은 수에 세지 않음
        list(others.get(0)).andExpect(jsonPath("$[*].id", contains(id)));
        unread(others.get(0)).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("만들기 규칙: 나 포함 3명 미만 400 GROUP_TOO_SMALL, 10명 초과 409 GROUP_FULL, 이름 21자 400, 없는 아이디 404")
    void createRules() throws Exception {
        createGroup(me, "둘", name(1), name(0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_TOO_SMALL"));
        createGroup(me, "많음", names.subList(1, 11).toArray(String[]::new)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_FULL"));
        createGroup(me, "가".repeat(21), name(1), name(2)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GROUP_TITLE"));
        createGroup(me, "없는", name(1), "nobody_here").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        createGroup(me, "열 명", names.subList(1, 10).toArray(String[]::new)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberCount").value(10));
    }

    @Test
    @DisplayName("목록: 1:1과 그룹이 마지막 메시지 최근 순으로 섞여 나온다")
    void listMixesDirectAndGroup() throws Exception {
        String group = groupId(createGroup(me, "모임", name(1), name(2)));
        String direct = JsonPath.read(mvc.perform(post("/api/v1/conversations/direct").servletPath("/api/v1")
                .with(authentication(member(me))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + name(3) + "\"}")).andReturn().getResponse().getContentAsString(), "$.id");
        insertMessage(Long.parseLong(direct), others.get(2), "1:1 새 글");

        list(me)
                .andExpect(jsonPath("$[*].id", contains(direct, group)))
                .andExpect(jsonPath("$[0].type").value("DIRECT"))
                .andExpect(jsonPath("$[0].partner.username").value(name(3)))
                .andExpect(jsonPath("$[0].members", hasSize(0)))
                .andExpect(jsonPath("$[1].type").value("GROUP"));

        insertMessage(Long.parseLong(group), others.get(0), "그룹 새 글");
        list(me).andExpect(jsonPath("$[*].id", contains(group, direct)));
    }

    @Test
    @DisplayName("초대: 멤버 누구나, 초대된 사람은 이전 대화를 전부 보지만 안 읽은 수는 0, '초대' 안내 메시지")
    void inviteSeesHistory() throws Exception {
        String id = groupId(createGroup(me, "모임", name(1), name(2)));
        long conversation = Long.parseLong(id);
        insertMessage(conversation, others.get(0), "초대 전 글");

        invite(others.get(1), id, name(4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(4))
                .andExpect(jsonPath("$.lastMessage.system.event").value("INVITED"))
                .andExpect(jsonPath("$.lastMessage.system.actor.username").value(name(2)))
                .andExpect(jsonPath("$.lastMessage.system.targets[*].username", contains(name(4))));

        long invitee = others.get(3);
        messages(invitee, id)
                .andExpect(jsonPath("$.items[*].type", contains("SYSTEM", "TEXT", "SYSTEM")))
                .andExpect(jsonPath("$.items[1].text").value("초대 전 글"))
                .andExpect(jsonPath("$.items[1].sender.username").value(name(1)));
        detail(invitee, id).andExpect(jsonPath("$.unreadCount").value(0));

        insertMessage(conversation, others.get(0), "초대 후 글");
        detail(invitee, id).andExpect(jsonPath("$.unreadCount").value(1));
        // 이미 있는 사람만 초대하면 그대로 (안내 메시지 없음)
        invite(me, id, name(1)).andExpect(jsonPath("$.lastMessage.text").value("초대 후 글"));
    }

    @Test
    @DisplayName("초대 규칙: 10명을 넘으면 409 GROUP_FULL, 1:1은 400 NOT_A_GROUP, 참여하지 않은 사람은 404")
    void inviteRules() throws Exception {
        String id = groupId(createGroup(me, "모임", names.subList(1, 9).toArray(String[]::new)));
        invite(me, id, name(9), name(10)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_FULL"));
        invite(me, id, name(9)).andExpect(status().isOk()).andExpect(jsonPath("$.memberCount").value(10));

        String direct = JsonPath.read(mvc.perform(post("/api/v1/conversations/direct").servletPath("/api/v1")
                .with(authentication(member(me))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + name(1) + "\"}")).andReturn().getResponse().getContentAsString(), "$.id");
        invite(me, direct, name(2)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_A_GROUP"));

        String notMine = groupId(createGroup(others.get(0), "남의 방", name(2), name(3)));
        invite(me, notMine, name(4)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("이름 변경 · 알림 끄기: 멤버 누구나 이름을 바꾸고 안내 메시지가 남음, 알림 끄기는 내 설정만")
    void renameAndMute() throws Exception {
        String id = groupId(createGroup(me, "모임", name(1), name(2)));

        update(others.get(1), id, "{\"title\":\"새 이름\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("새 이름"))
                .andExpect(jsonPath("$.lastMessage.system.event").value("RENAMED"))
                .andExpect(jsonPath("$.lastMessage.system.title").value("새 이름"));
        update(me, id, "{\"muted\":true}").andExpect(jsonPath("$.muted").value(true));
        detail(others.get(0), id).andExpect(jsonPath("$.muted").value(false));
        update(me, id, "{\"title\":\" \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GROUP_TITLE"));
    }

    @Test
    @DisplayName("나가기: 남은 사람에게 '나가기' 안내, 나간 사람 목록에서 빠짐, 다시 초대하면 이전 대화를 전부 봄")
    void leaveAndReinvite() throws Exception {
        String id = groupId(createGroup(me, "모임", name(1), name(2)));
        long leaver = others.get(1);

        mvc.perform(delete("/api/v1/conversations/" + id + "/members/me").servletPath("/api/v1")
                .with(authentication(member(leaver))).with(csrf())).andExpect(status().isNoContent());

        detail(me, id)
                .andExpect(jsonPath("$.memberCount").value(2))
                .andExpect(jsonPath("$.lastMessage.system.event").value("LEFT"))
                .andExpect(jsonPath("$.lastMessage.system.actor.username").value(name(2)));
        list(leaver).andExpect(jsonPath("$", hasSize(0)));

        insertMessage(Long.parseLong(id), me, "나간 동안 글");
        invite(me, id, name(2)).andExpect(jsonPath("$.memberCount").value(3));
        messages(leaver, id).andExpect(jsonPath("$.items[*].type", contains("SYSTEM", "SYSTEM", "TEXT", "SYSTEM")));
    }

    // ---- 요청 ----

    private ResultActions createGroup(long userId, String title, String... usernames) throws Exception {
        String list = java.util.Arrays.stream(usernames).map(u -> "\"" + u + "\"").collect(Collectors.joining(","));
        return mvc.perform(post("/api/v1/conversations/group").servletPath("/api/v1")
                .with(authentication(member(userId))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"usernames\":[" + list + "]}"));
    }

    private static String groupId(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions invite(long userId, String id, String... usernames) throws Exception {
        String list = java.util.Arrays.stream(usernames).map(u -> "\"" + u + "\"").collect(Collectors.joining(","));
        return mvc.perform(post("/api/v1/conversations/" + id + "/members").servletPath("/api/v1")
                .with(authentication(member(userId))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernames\":[" + list + "]}"));
    }

    private ResultActions update(long userId, String id, String json) throws Exception {
        return mvc.perform(patch("/api/v1/conversations/" + id).servletPath("/api/v1")
                .with(authentication(member(userId))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions list(long userId) throws Exception {
        return mvc.perform(get("/api/v1/conversations").servletPath("/api/v1").with(authentication(member(userId))));
    }

    private ResultActions unread(long userId) throws Exception {
        return mvc.perform(get("/api/v1/conversations/unread-count").servletPath("/api/v1").with(authentication(member(userId))));
    }

    private ResultActions detail(long userId, String id) throws Exception {
        return mvc.perform(get("/api/v1/conversations/" + id).servletPath("/api/v1").with(authentication(member(userId))));
    }

    private ResultActions messages(long userId, String id) throws Exception {
        return mvc.perform(get("/api/v1/conversations/" + id + "/messages").servletPath("/api/v1")
                .with(authentication(member(userId))));
    }

    // ---- 데이터 ----

    private static String randomUsername() {
        return "g" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private long insertUser(String username) {
        long id = jdbc.queryForObject("INSERT INTO users (username, email) VALUES (?, ?) RETURNING id",
                Long.class, username, username + "@example.com");
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, now() FROM terms WHERE is_required = TRUE
                """, id);
        return id;
    }

    private void insertMessage(long conversationId, long senderId, String content) {
        jdbc.update("""
                INSERT INTO messages (conversation_id, sender_id, client_message_id, content, created_at)
                VALUES (?, ?, gen_random_uuid(), ?, ?)
                """, conversationId, senderId, content, OffsetDateTime.now().plusSeconds(++messageSeq));
        assertThat(messageSeq).isPositive();
    }

    private static UsernamePasswordAuthenticationToken member(long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
