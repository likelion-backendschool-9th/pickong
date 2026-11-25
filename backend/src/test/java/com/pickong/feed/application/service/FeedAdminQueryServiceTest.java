package com.pickong.feed.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.pickong.common.domain.Visibility;
import com.pickong.feed.application.dto.AdminFeedView;
import com.pickong.feed.application.dto.AdminFeedView.Author;
import com.pickong.feed.application.port.out.AdminFeedQueryPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedAdminQueryServiceTest {
    @Mock AdminFeedQueryPort port;
    private FeedAdminQueryService service;

    @BeforeEach
    void setUp() {
        service = new FeedAdminQueryService(port);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t\n"})
    void emptySearchPreservesTheQueryOrder(String query) {
        List<AdminFeedView> rows = List.of(feed("첫 글", "작성자"), feed("다음 글", null));
        given(port.findAll()).willReturn(rows);
        assertThat(service.list(query)).containsExactlyElementsOf(rows);
        verify(port).findAll();
    }

    @Test
    void searchesOnlyTitleAndNicknameCaseInsensitively() {
        AdminFeedView title = feed("Travel 기록", "다른 작성자");
        AdminFeedView author = feed("다른 글", "TravelWriter");
        given(port.findAll()).willReturn(List.of(title, author, feed("다른 글", null)));
        assertThat(service.list("  TRAVEL  ")).containsExactly(title, author);
        assertThat(service.list("private_username")).isEmpty();
    }

    @Test
    void wildcardAndSqlSyntaxAreLiteralSearchText() {
        AdminFeedView literal = feed("100%_기록", "작성자");
        given(port.findAll()).willReturn(List.of(literal, feed("다른 글", null)));
        assertThat(service.list("%_")).containsExactly(literal);
        assertThat(service.list("' OR 1=1 --")).isEmpty();
    }

    private static AdminFeedView feed(String title, String nickname) {
        return new AdminFeedView(UUID.randomUUID(), title,
                nickname == null ? null : new Author(1, "private_username", nickname, null),
                Visibility.PUBLIC, false, 0, OffsetDateTime.parse("2026-11-25T03:00:00Z"));
    }
}
