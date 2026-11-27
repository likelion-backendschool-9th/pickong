package com.pickong.feed.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.domain.ViewerRelation;
import com.pickong.common.domain.Visibility;
import com.pickong.feed.domain.model.Feed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** 공개 범위 3종 × 보는 사람 관계 4종 전부를 확인함 */
class FeedVisibilityRulesTest {

    @ParameterizedTest(name = "{0} 게시물을 {1}가 보면 → {2}")
    @CsvSource({
            "PUBLIC,    ANONYMOUS, 열람",
            "PUBLIC,    OTHER,     열람",
            "PUBLIC,    FOLLOWER,  열람",
            "PUBLIC,    OWNER,     열람",
            "FOLLOWERS, ANONYMOUS, 404",
            "FOLLOWERS, OTHER,     404",
            "FOLLOWERS, FOLLOWER,  열람",
            "FOLLOWERS, OWNER,     열람",
            "PRIVATE,   ANONYMOUS, 404",
            "PRIVATE,   OTHER,     404",
            "PRIVATE,   FOLLOWER,  404",
            "PRIVATE,   OWNER,     열람",
    })
    @DisplayName("열람과 반응 추가는 같은 공개 범위 규칙을 따름")
    void visibilityMatrix(Visibility visibility, ViewerRelation relation, String expected) {
        Feed feed = Feed.builder().userId(1L).visibility(visibility).build();

        if (expected.equals("열람")) {
            assertThatCode(() -> feed.ensureReadable(relation, false)).doesNotThrowAnyException();
            assertThatCode(() -> feed.ensureAcceptsInteraction(relation)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> feed.ensureReadable(relation, false))
                    .extracting("errorCode").isEqualTo(FeedErrorCode.FEED_NOT_FOUND);
            assertThatThrownBy(() -> feed.ensureAcceptsInteraction(relation))
                    .extracting("errorCode").isEqualTo(FeedErrorCode.FEED_NOT_FOUND);
        }
    }

    @ParameterizedTest
    @EnumSource(Visibility.class)
    @DisplayName("관리자는 공개 범위와 관계없이 열람 가능")
    void adminReadsAnyVisibility(Visibility visibility) {
        Feed feed = Feed.builder().userId(1L).visibility(visibility).build();

        assertThatCode(() -> feed.ensureReadable(null, true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("관계를 모르면(null) 비로그인과 같게 판정함")
    void nullRelationIsAnonymous() {
        Feed publicFeed = Feed.builder().userId(1L).visibility(Visibility.PUBLIC).build();
        Feed followersFeed = Feed.builder().userId(1L).visibility(Visibility.FOLLOWERS).build();

        assertThatCode(() -> publicFeed.ensureReadable(null, false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> followersFeed.ensureReadable(null, false))
                .extracting("errorCode").isEqualTo(FeedErrorCode.FEED_NOT_FOUND);
    }

    @Test
    @DisplayName("공개 범위가 비어 있으면 본인도 404 (알 수 없는 값은 열지 않음)")
    void unknownVisibilityIsClosed() {
        Feed feed = Feed.builder().userId(1L).build();

        assertThatThrownBy(() -> feed.ensureReadable(ViewerRelation.OWNER, false))
                .extracting("errorCode").isEqualTo(FeedErrorCode.FEED_NOT_FOUND);
    }
}
