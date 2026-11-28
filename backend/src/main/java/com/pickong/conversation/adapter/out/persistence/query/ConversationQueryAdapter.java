package com.pickong.conversation.adapter.out.persistence.query;

import com.pickong.conversation.application.dto.ConversationPartner;
import com.pickong.conversation.application.dto.ConversationSummaryResult;
import com.pickong.conversation.application.dto.GroupMemberResult;
import com.pickong.conversation.application.dto.MessageResult;
import com.pickong.conversation.application.dto.SentMessage;
import com.pickong.conversation.application.port.out.ConversationQueryPort;
import com.pickong.conversation.domain.vo.ConversationType;
import com.pickong.conversation.domain.vo.MessageType;
import com.pickong.conversation.domain.vo.SystemEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 대화 목록 · 상세 · 메시지 내역 · 안 읽은 수를 읽습니다. 상대 · 참여자 · 보낸 사람은 users를 읽기만 하고,
 * user 모듈의 저장소와 엔티티는 주입하지 않습니다 (blueprint 6.1 조회용 JOIN 예외).
 * <p>
 * "볼 수 있는 메시지" = 지우지 않았고(deleted_at IS NULL), 1:1이면 내 참여 시각(joined_at) 이후(그룹은 처음부터).
 * 안 읽은 메시지 = 그중 다른 사람이 보낸 글(TEXT, 안내 메시지 제외)이고 내 마지막 읽은 메시지보다 뒤에 있는 것.
 */
@Component
public class ConversationQueryAdapter implements ConversationQueryPort {

    /** 1:1은 참여 시각 이후, 그룹은 처음부터 (메시지 별칭 m 기준) */
    private static final String VISIBLE = "m.deleted_at IS NULL AND (c.type = 'GROUP' OR m.created_at >= me.joined_at)";

    /** 참여 중인 대화방 + 마지막 메시지 + 안 읽은 수. 참여자는 따로 읽어 붙입니다. 뒤에 조건과 정렬을 붙여 씁니다. */
    private static final String SELECT_SUMMARY = """
            SELECT c.id, c.type, c.title, c.created_at AS conversation_created_at, me.joined_at, me.muted,
                   lm.id AS m_id, lm.sender_id AS m_sender_id, lm.type AS m_type, lm.content AS m_content,
                   CAST(lm.system_payload AS TEXT) AS m_system, lm.created_at AS m_created_at,
                   ls.public_id AS s_public_id, ls.username AS s_username, ls.display_name AS s_name,
                   ls.profile_image_url AS s_image,
                   (SELECT count(*)
                      FROM messages m
                     WHERE m.conversation_id = c.id
                       AND %s
                       AND m.type = 'TEXT'
                       AND m.sender_id <> me.user_id
                       AND m.id > COALESCE(me.last_read_message_id, 0)) AS unread_count
              FROM conversation_members me
              JOIN conversations c ON c.id = me.conversation_id
              LEFT JOIN LATERAL (
                   SELECT m.id, m.sender_id, m.type, m.content, m.system_payload, m.created_at
                     FROM messages m
                    WHERE m.conversation_id = c.id
                      AND %s
                    ORDER BY m.id DESC
                    LIMIT 1) lm ON TRUE
              LEFT JOIN users ls ON ls.id = lm.sender_id
             WHERE me.user_id = :userId
               AND me.left_at IS NULL
            """.formatted(VISIBLE, VISIBLE);

    /** 대화방들의 참여자 (나간 사람 포함, 들어온 순). 1:1 상대는 나갔어도 보여 주고, 그룹은 참여 중인 사람만 씁니다. */
    private static final String SELECT_MEMBERS = """
            SELECT cm.conversation_id, cm.user_id, cm.left_at, u.public_id, u.username, u.display_name, u.profile_image_url
              FROM conversation_members cm
              JOIN users u ON u.id = cm.user_id
             WHERE cm.conversation_id IN (:conversationIds)
             ORDER BY cm.conversation_id, cm.id
            """;

    private static final String COUNT_UNREAD = """
            SELECT count(*)
              FROM conversation_members me
              JOIN conversations c ON c.id = me.conversation_id
              JOIN messages m ON m.conversation_id = me.conversation_id
             WHERE me.user_id = :userId
               AND me.left_at IS NULL
               AND %s
               AND m.type = 'TEXT'
               AND m.sender_id <> me.user_id
               AND m.id > COALESCE(me.last_read_message_id, 0)
            """.formatted(VISIBLE);

    private static final String SELECT_MESSAGES = """
            SELECT m.id AS m_id, m.conversation_id, m.sender_id AS m_sender_id, m.type AS m_type, m.content AS m_content,
                   CAST(m.system_payload AS TEXT) AS m_system, m.created_at AS m_created_at, m.client_message_id,
                   s.public_id AS s_public_id, s.username AS s_username, s.display_name AS s_name,
                   s.profile_image_url AS s_image
              FROM messages m
              JOIN users s ON s.id = m.sender_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    public ConversationQueryAdapter(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<ConversationSummaryResult> findSummaries(long userId) {
        String sql = SELECT_SUMMARY + " AND lm.id IS NOT NULL ORDER BY lm.id DESC";
        return withMembers(userId, jdbc.query(sql, new MapSqlParameterSource("userId", userId), (rs, n) -> row(rs, userId)));
    }

    @Override
    public Optional<ConversationSummaryResult> findSummary(long userId, long conversationId) {
        String sql = SELECT_SUMMARY + " AND c.id = :conversationId";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("conversationId", conversationId);
        return withMembers(userId, jdbc.query(sql, params, (rs, n) -> row(rs, userId))).stream().findFirst();
    }

    @Override
    public long countUnread(long userId) {
        Long count = jdbc.queryForObject(COUNT_UNREAD, new MapSqlParameterSource("userId", userId), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<MessageResult> findMessagesBefore(long viewerId, long conversationId, OffsetDateTime visibleFrom,
                                                  Long beforeId, int limit) {
        // null 파라미터를 ":x IS NULL" 한 줄로 쓰면 PostgreSQL이 타입을 추론하지 못해 조건을 나눠 붙입니다.
        String sql = SELECT_MESSAGES + messageFilter(visibleFrom)
                + (beforeId == null ? "" : " AND m.id < :beforeId") + " ORDER BY m.id DESC LIMIT :limit";
        MapSqlParameterSource params = messageParams(conversationId, visibleFrom, limit).addValue("beforeId", beforeId);
        return jdbc.query(sql, params, (rs, n) -> message(rs, rs.getLong("conversation_id"), viewerId));
    }

    @Override
    public List<MessageResult> findMessagesAfter(long viewerId, long conversationId, OffsetDateTime visibleFrom,
                                                 long afterId, int limit) {
        String sql = SELECT_MESSAGES + messageFilter(visibleFrom) + " AND m.id > :afterId ORDER BY m.id ASC LIMIT :limit";
        MapSqlParameterSource params = messageParams(conversationId, visibleFrom, limit).addValue("afterId", afterId);
        return jdbc.query(sql, params, (rs, n) -> message(rs, rs.getLong("conversation_id"), viewerId));
    }

    @Override
    public Optional<SentMessage> findSentMessage(long messageId) {
        String sql = SELECT_MESSAGES + " WHERE m.id = :id";
        return jdbc.query(sql, new MapSqlParameterSource("id", messageId), (rs, n) -> {
            long senderId = rs.getLong("m_sender_id");
            return new SentMessage(message(rs, rs.getLong("conversation_id"), senderId), senderId,
                    rs.getObject("client_message_id", UUID.class));
        }).stream().findFirst();
    }

    @Override
    public Optional<UUID> findUserPublicId(long userId) {
        return jdbc.queryForList("SELECT public_id FROM users WHERE id = :id", new MapSqlParameterSource("id", userId),
                UUID.class).stream().findFirst();
    }

    @Override
    public List<SystemEvent.Participant> findParticipants(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SystemEvent.Participant> byId = new LinkedHashMap<>();
        jdbc.query("SELECT id, public_id, username FROM users WHERE id IN (:ids)", new MapSqlParameterSource("ids", userIds),
                rs -> {
                    byId.put(rs.getLong("id"),
                            new SystemEvent.Participant(rs.getObject("public_id", UUID.class), rs.getString("username")));
                });
        return userIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    // ---- 조립 ----

    private static String messageFilter(OffsetDateTime visibleFrom) {
        return " WHERE m.conversation_id = :conversationId AND m.deleted_at IS NULL"
                + (visibleFrom == null ? "" : " AND m.created_at >= :visibleFrom");
    }

    private static MapSqlParameterSource messageParams(long conversationId, OffsetDateTime visibleFrom, int limit) {
        return new MapSqlParameterSource()
                .addValue("conversationId", conversationId)
                .addValue("visibleFrom", visibleFrom)
                .addValue("limit", limit);
    }

    /** 참여자를 한 번에 읽어 1:1 상대 · 그룹 참여자를 붙입니다. */
    private List<ConversationSummaryResult> withMembers(long viewerId, List<Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<MemberRow>> members = new LinkedHashMap<>();
        jdbc.query(SELECT_MEMBERS, new MapSqlParameterSource("conversationIds", rows.stream().map(Row::id).toList()), rs -> {
            members.computeIfAbsent(rs.getLong("conversation_id"), k -> new ArrayList<>()).add(new MemberRow(
                    rs.getLong("user_id"),
                    rs.getObject("left_at") == null,
                    new ConversationPartner(rs.getObject("public_id", UUID.class), rs.getString("username"),
                            rs.getString("display_name"), rs.getString("profile_image_url"))));
        });
        return rows.stream().map(row -> {
            List<MemberRow> all = members.getOrDefault(row.id(), List.of());
            boolean group = row.type() == ConversationType.GROUP;
            ConversationPartner partner = group ? null : all.stream()
                    .filter(m -> m.userId() != viewerId).map(MemberRow::user).findFirst().orElse(null);
            List<GroupMemberResult> groupMembers = group ? all.stream().filter(MemberRow::active)
                    .map(m -> new GroupMemberResult(m.user().publicId(), m.user().username(), m.user().name(),
                            m.user().profileImageUrl(), m.userId() == viewerId))
                    .toList() : List.of();
            int memberCount = group ? groupMembers.size() : 2;
            return new ConversationSummaryResult(row.id(), row.type(), row.title(), partner, groupMembers, memberCount,
                    row.lastMessage(), row.unreadCount(), row.muted(), row.createdAt(), row.updatedAt());
        }).toList();
    }

    private Row row(ResultSet rs, long viewerId) throws SQLException {
        long id = rs.getLong("id");
        MessageResult last = rs.getObject("m_id") == null ? null : message(rs, id, viewerId);
        OffsetDateTime updatedAt = last != null ? last.createdAt() : rs.getObject("joined_at", OffsetDateTime.class);
        return new Row(id, ConversationType.valueOf(rs.getString("type")), rs.getString("title"), last,
                rs.getLong("unread_count"), rs.getBoolean("muted"),
                rs.getObject("conversation_created_at", OffsetDateTime.class), updatedAt);
    }

    /** m_ · s_ 별칭 컬럼에서 메시지 하나를 읽습니다. */
    private MessageResult message(ResultSet rs, long conversationId, long viewerId) throws SQLException {
        MessageType type = MessageType.valueOf(rs.getString("m_type"));
        String system = rs.getString("m_system");
        return new MessageResult(
                rs.getLong("m_id"),
                conversationId,
                new ConversationPartner(rs.getObject("s_public_id", UUID.class), rs.getString("s_username"),
                        rs.getString("s_name"), rs.getString("s_image")),
                rs.getLong("m_sender_id") == viewerId,
                type,
                rs.getString("m_content"),
                system == null ? null : json.readValue(system, SystemEvent.class),
                rs.getObject("m_created_at", OffsetDateTime.class));
    }

    private record Row(long id, ConversationType type, String title, MessageResult lastMessage, long unreadCount,
                       boolean muted, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    private record MemberRow(long userId, boolean active, ConversationPartner user) {
    }
}
