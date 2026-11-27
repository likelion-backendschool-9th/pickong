package com.pickong.savedplace.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.error.BusinessException;
import com.pickong.savedplace.domain.vo.SavedPlaceSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SourceTypesTest {

    @Test
    @DisplayName("프론트 POST는 FEED, 나머지는 같은 이름. 없으면 SEARCH")
    void fromApi() {
        assertThat(SourceTypes.fromApi("POST")).isEqualTo(SavedPlaceSource.FEED);
        assertThat(SourceTypes.fromApi("COLLECTION")).isEqualTo(SavedPlaceSource.COLLECTION);
        assertThat(SourceTypes.fromApi("LINK_IMPORT")).isEqualTo(SavedPlaceSource.LINK_IMPORT);
        assertThat(SourceTypes.fromApi("CUSTOM")).isEqualTo(SavedPlaceSource.CUSTOM);
        assertThat(SourceTypes.fromApi(null)).isEqualTo(SavedPlaceSource.SEARCH);
    }

    @Test
    @DisplayName("서버만 쓰는 PLAN_VISIT과 모르는 값은 거절한다")
    void rejectsUnknown() {
        for (String type : new String[] {"PLAN_VISIT", "FEED", "DIRECT", "x"}) {
            assertThatThrownBy(() -> SourceTypes.fromApi(type)).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("응답은 FEED를 POST로, 프론트에 없는 PLAN_VISIT은 SEARCH로 보낸다")
    void toApi() {
        assertThat(SourceTypes.toApi("FEED")).isEqualTo("POST");
        assertThat(SourceTypes.toApi("CUSTOM")).isEqualTo("CUSTOM");
        assertThat(SourceTypes.toApi("PLAN_VISIT")).isEqualTo("SEARCH");
        assertThat(SourceTypes.defaultLabel("POST")).isEqualTo("게시물에서 저장");
    }
}
