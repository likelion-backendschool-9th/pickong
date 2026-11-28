package com.pickong.savedplace.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.error.BusinessException;
import com.pickong.savedplace.domain.SavedPlaceErrorCode;
import com.pickong.savedplace.domain.vo.SavedPlaceOrigin;
import com.pickong.savedplace.domain.vo.SavedPlaceSource;
import com.pickong.savedplace.domain.vo.VisitStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SavedPlaceTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 26, 9, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T1 = T0.plusHours(1);

    @Test
    @DisplayName("새로 저장하면 가고 싶은 곳 · 메모 없음이고 출처를 그대로 남긴다")
    void create() {
        SavedPlace saved = SavedPlace.create(1L, 2L, new SavedPlaceOrigin(SavedPlaceSource.FEED, 9L, "게시물", null), T0);

        assertThat(saved.getVisitStatus()).isEqualTo(VisitStatus.WANT_TO_GO);
        assertThat(saved.getMemo()).isNull();
        assertThat(saved.getVisitedAt()).isNull();
        assertThat(saved.getSourceType()).isEqualTo(SavedPlaceSource.FEED);
        assertThat(saved.getSourceFeedId()).isEqualTo(9L);
        assertThat(saved.getSourceLabel()).isEqualTo("게시물");
        assertThat(saved.getCreatedAt()).isEqualTo(T0).isEqualTo(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("출처가 없으면 직접 저장(SEARCH)")
    void createWithoutOrigin() {
        assertThat(SavedPlace.create(1L, 2L, null, T0).getSourceType()).isEqualTo(SavedPlaceSource.SEARCH);
    }

    @Test
    @DisplayName("VISITED로 바꾸면 방문 시각을 남기고, 되돌리면 지운다. 같은 값이면 수정 시각도 그대로")
    void changeVisitStatus() {
        SavedPlace saved = SavedPlace.create(1L, 2L, null, T0);

        saved.changeVisitStatus(VisitStatus.VISITED, T1);
        assertThat(saved.getVisitedAt()).isEqualTo(T1);
        assertThat(saved.getUpdatedAt()).isEqualTo(T1);

        saved.changeVisitStatus(VisitStatus.VISITED, T1.plusHours(1));
        assertThat(saved.getVisitedAt()).isEqualTo(T1);

        saved.changeVisitStatus(VisitStatus.WANT_TO_GO, T1.plusHours(2));
        assertThat(saved.getVisitedAt()).isNull();
    }

    @Test
    @DisplayName("메모는 앞뒤 공백을 지우고, 빈 문자열은 메모 없음, 500자를 넘으면 거절한다")
    void changeMemo() {
        SavedPlace saved = SavedPlace.create(1L, 2L, null, T0);

        saved.changeMemo("  창가 자리  ", T1);
        assertThat(saved.getMemo()).isEqualTo("창가 자리");
        saved.changeMemo("   ", T1);
        assertThat(saved.getMemo()).isNull();
        saved.changeMemo("가".repeat(500), T1);
        assertThat(saved.getMemo()).hasSize(500);

        assertThatThrownBy(() -> saved.changeMemo("가".repeat(501), T1))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SavedPlaceErrorCode.MEMO_TOO_LONG));
    }
}
