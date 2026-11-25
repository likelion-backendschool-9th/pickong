package com.pickong.moderation.adapter.out.persistence.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickong.TestcontainersConfiguration;
import com.pickong.moderation.application.dto.ReportTargetLookup;
import com.pickong.moderation.application.port.out.ReportTargetLookupPort;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 회원 신고 대상 조회. PostGIS 컨테이너가 필요합니다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReportTargetLookupAdapterIntegrationTest {

    @Autowired
    ReportTargetLookupPort lookup;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> commentIds = new ArrayList<>();
    private final List<Long> feedIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void deleteRows() {
        commentIds.forEach(id -> jdbc.update("DELETE FROM comments WHERE id = ?", id));
        feedIds.forEach(id -> jdbc.update("DELETE FROM feeds WHERE id = ?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
        commentIds.clear();
        feedIds.clear();
        userIds.clear();
    }

    @Test
    @DisplayName("공개 게시물은 작성자 id와 본문 원문을 돌려준다")
    void publicFeedKeepsTheAuthorAndOriginalBody() {
        long author = insertUser();
        long reporter = insertUser();
        String body = "  첫 줄\r\n둘째 줄  ";
        long feed = insertFeed(author, body, "PUBLIC");

        ReportTargetLookup found = lookup.findReportable(reporter, ReportTargetType.FEED, feed).orElseThrow();

        assertThat(found.targetType()).isEqualTo(ReportTargetType.FEED);
        assertThat(found.targetId()).isEqualTo(feed);
        assertThat(found.authorId()).isEqualTo(author);
        assertThat(found.text()).isEqualTo(body);
    }

    @Test
    @DisplayName("게시물 본문이 없으면 null이고 빈 문자열은 빈 본문이다")
    void feedDistinguishesMissingTextFromBlankText() {
        long author = insertUser();
        long reporter = insertUser();
        long missing = insertFeed(author, null, "PUBLIC");
        long blank = insertFeed(author, "", "PUBLIC");

        assertThat(lookup.findReportable(reporter, ReportTargetType.FEED, missing).orElseThrow().text()).isNull();
        assertThat(lookup.findReportable(reporter, ReportTargetType.FEED, blank).orElseThrow().text()).isEmpty();
    }

    @Test
    @DisplayName("없는 게시물, 삭제, 숨김, 비공개, 팔로워 공개는 다른 회원에게 반환하지 않는다")
    void inaccessibleFeedsAreEmpty() {
        long author = insertUser();
        long reporter = insertUser();
        long deleted = insertFeed(author, "lookup-it-삭제", "PUBLIC");
        long hidden = insertFeed(author, "lookup-it-숨김", "PUBLIC");
        long priv = insertFeed(author, "lookup-it-비공개", "PRIVATE");
        long followers = insertFeed(author, "lookup-it-팔로워", "FOLLOWERS");
        jdbc.update("UPDATE feeds SET deleted_at = now() WHERE id = ?", deleted);
        jdbc.update("UPDATE feeds SET hidden_at = now() WHERE id = ?", hidden);

        assertThat(lookup.findReportable(reporter, ReportTargetType.FEED, deleted)).isEmpty();
        assertThat(lookup.findReportable(reporter, ReportTargetType.FEED, hidden)).isEmpty();
        assertThat(lookup.findReportable(reporter, ReportTargetType.FEED, priv)).isEmpty();
        assertThat(lookup.findReportable(reporter, ReportTargetType.FEED, followers)).isEmpty();
        assertThat(lookup.findReportable(reporter, ReportTargetType.FEED, 9_000_000_000L)).isEmpty();
    }

    @Test
    @DisplayName("작성자 본인의 비공개 게시물은 작성자 id를 돌려준다")
    void ownerCanReadTheirOwnPrivateFeed() {
        long author = insertUser();
        long feed = insertFeed(author, "lookup-it-내 글", "PRIVATE");

        ReportTargetLookup found = lookup.findReportable(author, ReportTargetType.FEED, feed).orElseThrow();

        assertThat(found.authorId()).isEqualTo(author);
        assertThat(found.text()).isEqualTo("lookup-it-내 글");
    }

    @Test
    @DisplayName("댓글은 댓글 작성자 id와 본문 원문을 돌려준다")
    void commentUsesTheCommentAuthorNotTheFeedAuthor() {
        long feedAuthor = insertUser();
        long commentAuthor = insertUser();
        long reporter = insertUser();
        long feed = insertFeed(feedAuthor, "lookup-it-상위", "PUBLIC");
        String body = "첫 줄\n둘째 줄";
        long comment = insertComment(feed, commentAuthor, body);

        ReportTargetLookup found = lookup.findReportable(reporter, ReportTargetType.COMMENT, comment).orElseThrow();

        assertThat(found.authorId()).isEqualTo(commentAuthor);
        assertThat(found.authorId()).isNotEqualTo(feedAuthor);
        assertThat(found.text()).isEqualTo(body);
    }

    @Test
    @DisplayName("댓글 빈 문자열은 빈 본문으로 유지한다")
    void blankCommentStaysBlank() {
        long author = insertUser();
        long reporter = insertUser();
        long feed = insertFeed(author, "lookup-it-상위", "PUBLIC");
        long comment = insertComment(feed, author, "");

        assertThat(lookup.findReportable(reporter, ReportTargetType.COMMENT, comment).orElseThrow().text()).isEmpty();
    }

    @Test
    @DisplayName("없는 댓글, 삭제·숨김 댓글, 상위 글이 삭제·숨김·비공개인 댓글은 반환하지 않는다")
    void inaccessibleCommentsAreEmpty() {
        long feedAuthor = insertUser();
        long commentAuthor = insertUser();
        long reporter = insertUser();
        long live = insertFeed(feedAuthor, "lookup-it-상위", "PUBLIC");
        long deletedFeed = insertFeed(feedAuthor, "lookup-it-삭제상위", "PUBLIC");
        long hiddenFeed = insertFeed(feedAuthor, "lookup-it-숨김상위", "PUBLIC");
        long privateFeed = insertFeed(feedAuthor, "lookup-it-비공개상위", "PRIVATE");
        long deletedComment = insertComment(live, commentAuthor, "lookup-it-삭제댓글");
        long hiddenComment = insertComment(live, commentAuthor, "lookup-it-숨김댓글");
        long onDeleted = insertComment(deletedFeed, commentAuthor, "lookup-it-삭제위");
        long onHidden = insertComment(hiddenFeed, commentAuthor, "lookup-it-숨김위");
        long onPrivate = insertComment(privateFeed, commentAuthor, "lookup-it-비공개위");
        jdbc.update("UPDATE comments SET deleted_at = now() WHERE id = ?", deletedComment);
        jdbc.update("UPDATE comments SET hidden_at = now() WHERE id = ?", hiddenComment);
        jdbc.update("UPDATE feeds SET deleted_at = now() WHERE id = ?", deletedFeed);
        jdbc.update("UPDATE feeds SET hidden_at = now() WHERE id = ?", hiddenFeed);

        assertThat(lookup.findReportable(reporter, ReportTargetType.COMMENT, 9_000_000_000L)).isEmpty();
        assertThat(lookup.findReportable(reporter, ReportTargetType.COMMENT, deletedComment)).isEmpty();
        assertThat(lookup.findReportable(reporter, ReportTargetType.COMMENT, hiddenComment)).isEmpty();
        assertThat(lookup.findReportable(reporter, ReportTargetType.COMMENT, onDeleted)).isEmpty();
        assertThat(lookup.findReportable(reporter, ReportTargetType.COMMENT, onHidden)).isEmpty();
        assertThat(lookup.findReportable(reporter, ReportTargetType.COMMENT, onPrivate)).isEmpty();
    }

    @Test
    @DisplayName("팔로워 공개 게시물의 댓글은 다른 회원에게 반환하지 않는다")
    void commentsOnFollowersFeedsAreEmpty() {
        long feedAuthor = insertUser();
        long commentAuthor = insertUser();
        long reporter = insertUser();
        long followers = insertFeed(feedAuthor, "lookup-it-팔로워상위", "FOLLOWERS");
        long comment = insertComment(followers, commentAuthor, "lookup-it-팔로워댓글");

        assertThat(lookup.findReportable(reporter, ReportTargetType.COMMENT, comment)).isEmpty();
    }

    @Test
    @DisplayName("게시물 작성자가 타인의 댓글을 조회하면 댓글 작성자 id를 돌려준다")
    void feedAuthorCanReadAnotherPersonsComment() {
        long feedAuthor = insertUser();
        long commentAuthor = insertUser();
        long feed = insertFeed(feedAuthor, "lookup-it-상위", "PUBLIC");
        long comment = insertComment(feed, commentAuthor, "lookup-it-타인댓글");

        ReportTargetLookup found = lookup.findReportable(feedAuthor, ReportTargetType.COMMENT, comment).orElseThrow();

        assertThat(found.authorId()).isEqualTo(commentAuthor);
        assertThat(found.authorId()).isNotEqualTo(feedAuthor);
        assertThat(found.text()).isEqualTo("lookup-it-타인댓글");
    }

    @Test
    @DisplayName("자기 댓글은 상위 글이 공개일 때 댓글 작성자 id를 돌려준다")
    void ownerCanReadTheirOwnComment() {
        long feedAuthor = insertUser();
        long commentAuthor = insertUser();
        long feed = insertFeed(feedAuthor, "lookup-it-상위", "PUBLIC");
        long comment = insertComment(feed, commentAuthor, "lookup-it-내 댓글");

        ReportTargetLookup found = lookup.findReportable(commentAuthor, ReportTargetType.COMMENT, comment).orElseThrow();

        assertThat(found.authorId()).isEqualTo(commentAuthor);
        assertThat(found.text()).isEqualTo("lookup-it-내 댓글");
    }

    @Test
    @DisplayName("회원과 컬렉션은 신고 대상 조회로 반환하지 않는다")
    void userAndCollectionAreNotReportableTargets() {
        long user = insertUser();

        assertThat(lookup.findReportable(user, ReportTargetType.USER, user)).isEmpty();
        assertThat(lookup.findReportable(user, ReportTargetType.COLLECTION, 1L)).isEmpty();
    }

    @Test
    @DisplayName("조회 결과에는 이메일 필드가 없다")
    void resultDoesNotCarryContactFields() {
        long author = insertUser();
        long reporter = insertUser();
        long feed = insertFeed(author, "lookup-it-본문", "PUBLIC");

        Optional<ReportTargetLookup> found = lookup.findReportable(reporter, ReportTargetType.FEED, feed);

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().toString()).doesNotContain("@");
    }

    private long insertUser() {
        String username = "l" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long id = jdbc.queryForObject(
                "INSERT INTO users (username, display_name, email) VALUES (?, ?, ?) RETURNING id",
                Long.class, username, "조회", username + "@example.com");
        userIds.add(id);
        return id;
    }

    private long insertFeed(long userId, String content, String visibility) {
        long id = jdbc.queryForObject(
                "INSERT INTO feeds (user_id, content, visibility) VALUES (?, ?, ?) RETURNING id",
                Long.class, userId, content, visibility);
        feedIds.add(id);
        return id;
    }

    private long insertComment(long feedId, long userId, String content) {
        long id = jdbc.queryForObject(
                "INSERT INTO comments (feed_id, user_id, content) VALUES (?, ?, ?) RETURNING id",
                Long.class, feedId, userId, content);
        commentIds.add(id);
        return id;
    }
}
