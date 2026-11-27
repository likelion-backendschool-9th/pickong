package com.pickong.conversation.adapter.out.persistence.query;

import com.pickong.conversation.application.dto.ConversationPartner;
import com.pickong.conversation.application.dto.ConversationSummaryResult;
import com.pickong.conversation.application.dto.MessageResult;
import com.pickong.conversation.application.port.out.ConversationQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 대화 목록 · 상세 · 메시지 내역 · 안 읽은 수를 읽습니다. 상대와 보낸 사람의 공개 id · 아이디 · 사진은 users를 읽기만 하고,
 * user 모듈의 저장소와 엔티티는 주입하지 않습니다 (blueprint 6.1 조회용 JOIN 예외).
 * <p>
 * 모든 쿼리의 "볼 수 있는 메시지"는 지우지 않았고(deleted_at IS NULL) 내 참여 시각(joined_at) 이후에 보낸 메시지입니다.
 * 안 읽은 메시지는 그중 상대가 보냈고 내 마지막 읽은 메시지보다 뒤에 있는 것입니다.
 */
@Component
public class ConversationQueryAdapter implements ConversationQueryPort {

    /** 참여 중인 1:1 대화방 + 상대 + 마지막 메시지 + 안 읽은 수. 뒤에 조건과 정렬을 붙여 씁니다. */
    private static final String SELECT_DIRECT_SUMMARY = """
            SELECT c.id, me.joined_at,
                   p.public_id AS partner_public_id, p.username AS partner_username,
                   p.display_name AS partner_name, p.profile_image_url AS partner_image_url,
                   lm.id AS last_id, ls.public_id AS last_sender_public_id, lm.sender_id AS last_sender_id,
                   lm.content AS last_content, lm.created_at AS last_created_at,
                   (SELECT count(*)
                      FROM messages u
                     WHERE u.conversation_id = c.id
                       AND u.deleted_at IS NULL
                       AND u.created_at >= me.joined_at
                       AND u.sender_id <> me.user_id
                       AND u.id > COALESCE(me.last_read_message_id, 0)) AS unread_count
              FROM conversation_members me
              JOIN conversations c ON c.id = me.conversation_id AND c.type = 'DIRECT'
              JOIN conversation_members pm ON pm.conversation_id = c.id AND pm.user_id <> me.user_id
              JOIN users p ON p.id = pm.user_id
              LEFT JOIN LATERAL (
                   SELECT m.id, m.sender_id, m.content, m.created_at
                     FROM messages m
                    WHERE m.conversation_id = c.id
                      AND m.deleted_at IS NULL
                      AND m.created_at >= me.joined_at
                    ORDER BY m.id DESC
                    LIMIT 1) lm ON TRUE
              LEFT JOIN users ls ON ls.id = lm.sender_id
             WHERE me.user_id = :userId
               AND me.left_at IS NULL
            """;

    private static final String COUNT_UNREAD = """
            SELECT count(*)
              FROM conversation_members me
              JOIN messages u ON u.conversation_id = me.conversation_id
             WHERE me.user_id = :userId
               AND me.left_at IS NULL
               AND u.deleted_at IS NULL
               AND u.created_at >= me.joined_at
               AND u.sender_id <> me.user_id
               AND u.id > COALESCE(me.last_read_message_id, 0)
            """;

    private static final String SELECT_MESSAGES = """
            SELECT m.id, m.conversation_id, m.sender_id, s.public_id AS sender_public_id, m.content, m.created_at
              FROM messages m
              JOIN users s ON s.id = m.sender_id
             WHERE m.conversation_id = :conversationId
               AND m.deleted_at IS NULL
               AND m.created_at >= :visibleFrom
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ConversationQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ConversationSummaryResult> findDirectSummaries(long userId) {
        String sql = SELECT_DIRECT_SUMMARY + " AND lm.id IS NOT NULL ORDER BY lm.id DESC";
        return jdbc.query(sql, new MapSqlParameterSource("userId", userId), (rs, rowNum) -> toSummary(rs, userId));
    }

    @Override
    public Optional<ConversationSummaryResult> findDirectSummary(long userId, long conversationId) {
        String sql = SELECT_DIRECT_SUMMARY + " AND c.id = :conversationId";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("conversationId", conversationId);
        return jdbc.query(sql, params, (rs, rowNum) -> toSummary(rs, userId)).stream().findFirst();
    }

    @Override
    public long countUnread(long userId) {
        Long count = jdbc.queryForObject(COUNT_UNREAD, new MapSqlParameterSource("userId", userId), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<MessageResult> findMessagesBefore(long viewerId, long conversationId, OffsetDateTime visibleFrom,
                                                  Long beforeId, int limit) {
        // beforeId가 null일 때 ":beforeId IS NULL" 한 줄로 쓰면 PostgreSQL이 파라미터 타입을 추론하지 못해 쿼리를 나눕니다.
        String sql = SELECT_MESSAGES + (beforeId == null ? "" : " AND m.id < :beforeId") + " ORDER BY m.id DESC LIMIT :limit";
        MapSqlParameterSource params = messageParams(conversationId, visibleFrom, limit).addValue("beforeId", beforeId);
        return jdbc.query(sql, params, (rs, rowNum) -> toMessage(rs, viewerId));
    }

    @Override
    public List<MessageResult> findMessagesAfter(long viewerId, long conversationId, OffsetDateTime visibleFrom,
                                                 long afterId, int limit) {
        String sql = SELECT_MESSAGES + " AND m.id > :afterId ORDER BY m.id ASC LIMIT :limit";
        MapSqlParameterSource params = messageParams(conversationId, visibleFrom, limit).addValue("afterId", afterId);
        return jdbc.query(sql, params, (rs, rowNum) -> toMessage(rs, viewerId));
    }

    private static MapSqlParameterSource messageParams(long conversationId, OffsetDateTime visibleFrom, int limit) {
        return new MapSqlParameterSource()
                .addValue("conversationId", conversationId)
                .addValue("visibleFrom", visibleFrom)
                .addValue("limit", limit);
    }

    private static ConversationSummaryResult toSummary(ResultSet rs, long viewerId) throws SQLException {
        ConversationPartner partner = new ConversationPartner(
                rs.getObject("partner_public_id", UUID.class),
                rs.getString("partner_username"),
                rs.getString("partner_name"),
                rs.getString("partner_image_url"));
        long conversationId = rs.getLong("id");
        Long lastId = rs.getObject("last_id", Long.class);
        MessageResult last = lastId == null ? null : new MessageResult(
                lastId,
                conversationId,
                rs.getObject("last_sender_public_id", UUID.class),
                rs.getLong("last_sender_id") == viewerId,
                rs.getString("last_content"),
                rs.getObject("last_created_at", OffsetDateTime.class));
        OffsetDateTime updatedAt = last != null ? last.createdAt() : rs.getObject("joined_at", OffsetDateTime.class);
        return new ConversationSummaryResult(conversationId, partner, last, rs.getLong("unread_count"), updatedAt);
    }

    private static MessageResult toMessage(ResultSet rs, long viewerId) throws SQLException {
        return new MessageResult(
                rs.getLong("id"),
                rs.getLong("conversation_id"),
                rs.getObject("sender_public_id", UUID.class),
                rs.getLong("sender_id") == viewerId,
                rs.getString("content"),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
