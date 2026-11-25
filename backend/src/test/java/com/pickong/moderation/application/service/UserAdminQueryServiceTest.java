package com.pickong.moderation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pickong.common.error.BusinessException;
import com.pickong.moderation.application.dto.AdminUserView;
import com.pickong.moderation.application.dto.AdminUserView.ActiveSanction;
import com.pickong.moderation.application.port.out.AdminUserQueryPort;
import com.pickong.moderation.domain.vo.SanctionType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserAdminQueryServiceTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T03:00:00Z");
    @Mock AdminUserQueryPort port;
    private UserAdminQueryService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminQueryService(port, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ALL", "  "})
    void defaultsAndSearchNormalization(String filter) {
        given(port.findUsers("이름", NOW)).willReturn(List.of(user(1, 0, false)));
        assertThat(service.list("  이름  ", filter)).extracting(AdminUserView::id).containsExactly(1L);
        verify(port).findUsers("이름", NOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "reported", "UNKNOWN", "ALL,REPORTED"})
    void rejectsInvalidFilterBeforeReading(String filter) {
        assertThatThrownBy(() -> service.list(null, filter)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode().name()).isEqualTo("INVALID_INPUT"));
        verifyNoInteractions(port);
    }

    @Test
    void reportedUsesHistoryAndSanctionedIncludesWarnings() {
        AdminUserView reported = user(1, 2, false);
        AdminUserView warning = user(2, 0, true);
        given(port.findUsers("", NOW)).willReturn(List.of(reported, warning, user(3, 0, false)));
        assertThat(service.list(null, "REPORTED")).containsExactly(reported);
        assertThat(service.list(null, "SANCTIONED")).containsExactly(warning);
    }

    @Test
    void sortsByReportCountThenIdDescending() {
        given(port.findUsers("", NOW)).willReturn(List.of(user(1, 2, false), user(2, 2, false), user(3, 3, false)));
        assertThat(service.list(null, null)).extracting(AdminUserView::id).containsExactly(3L, 2L, 1L);
    }

    private static AdminUserView user(long id, long reports, boolean warning) {
        List<ActiveSanction> sanctions = warning
                ? List.of(new ActiveSanction(10, SanctionType.WARNING, "경고", NOW, null)) : List.of();
        return new AdminUserView(id, "user" + id, "이름", null, reports, 1, "ACTIVE", sanctions);
    }
}
