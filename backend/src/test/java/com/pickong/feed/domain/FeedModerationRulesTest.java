package com.pickong.feed.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.domain.Visibility;
import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.model.Feed;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class FeedModerationRulesTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T03:00:00Z");

    @Test
    void hidingAndRestoringPreserveVisibilityAndCountersAndNoopPreservesTime() {
        Feed feed = Feed.builder().id(1L).userId(2L).visibility(Visibility.PRIVATE)
                .likeCount(7).commentCount(3).bookmarkCount(4).build();
        assertThat(feed.changeHidden(true, NOW)).isTrue();
        assertThat(feed.changeHidden(true, NOW.plusHours(1))).isFalse();
        assertThat(feed.getHiddenAt()).isEqualTo(NOW);
        assertThat(feed.getUpdatedAt()).isEqualTo(NOW);
        assertThat(feed.changeHidden(false, NOW.plusHours(2))).isTrue();
        assertThat(feed.getHiddenAt()).isNull();
        assertThat(feed.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(feed.getLikeCount()).isEqualTo(7);
        assertThat(feed.getCommentCount()).isEqualTo(3);
        assertThat(feed.getBookmarkCount()).isEqualTo(4);
    }

    @Test
    void onlyOwnerOrAdminCanReadHiddenFeedAndNobodyCanAddInteractions() {
        Feed feed = Feed.builder().userId(2L).hiddenAt(NOW).build();
        feed.ensureReadable(2L, false);
        feed.ensureReadable(null, true);
        assertThatThrownBy(() -> feed.ensureReadable(null, false)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> feed.ensureReadable(3L, false)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(feed::ensureAcceptsInteraction).isInstanceOf(BusinessException.class);
    }

    @Test
    void deletedFeedCannotBeReadOrRestoredEvenByOwnerOrAdmin() {
        Feed feed = Feed.builder().userId(2L).hiddenAt(NOW).deletedAt(NOW).build();
        assertThatThrownBy(() -> feed.ensureReadable(2L, true)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> feed.changeHidden(false, NOW.plusHours(1))).isInstanceOf(BusinessException.class);
        assertThat(feed.getHiddenAt()).isEqualTo(NOW);
    }
}
