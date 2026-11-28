package com.pickong.savedplace.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SavedPlaceOriginTest {

    @Test
    @DisplayName("게시물 id는 FEED일 때만, 컬렉션 id는 COLLECTION일 때만, 원문 URL은 LINK_IMPORT일 때만 남긴다 (ERD 제약 ④)")
    void keepsRefsOnlyForMatchingType() {
        assertThat(new SavedPlaceOrigin(SavedPlaceSource.SEARCH, 9L, null, "https://a.com").feedId()).isNull();
        assertThat(new SavedPlaceOrigin(SavedPlaceSource.SEARCH, 9L, null, "https://a.com").url()).isNull();
        assertThat(new SavedPlaceOrigin(SavedPlaceSource.FEED, 9L, null, "https://a.com").url()).isNull();
        assertThat(new SavedPlaceOrigin(SavedPlaceSource.LINK_IMPORT, 9L, null, "https://a.com").feedId()).isNull();
        assertThat(new SavedPlaceOrigin(SavedPlaceSource.LINK_IMPORT, null, null, "https://a.com").url()).isEqualTo("https://a.com");
        assertThat(new SavedPlaceOrigin(SavedPlaceSource.SEARCH, 9L, null, null).refId()).isNull();
        SavedPlaceOrigin fromCollection = new SavedPlaceOrigin(SavedPlaceSource.COLLECTION, 7L, null, null);
        assertThat(fromCollection.collectionId()).isEqualTo(7L);
        assertThat(fromCollection.feedId()).isNull();
    }

    @Test
    @DisplayName("링크 가져오기는 http · https 주소만 원문 URL로 받는다")
    void webUrlOnly() {
        assertThat(new SavedPlaceOrigin(SavedPlaceSource.LINK_IMPORT, null, null, "job-1001").url()).isNull();
        assertThat(new SavedPlaceOrigin(SavedPlaceSource.LINK_IMPORT, null, null, "javascript:alert(1)").url()).isNull();
        assertThat(new SavedPlaceOrigin(SavedPlaceSource.LINK_IMPORT, null, null, "HTTP://A.COM/x").url()).isEqualTo("HTTP://A.COM/x");
    }

    @Test
    @DisplayName("문구는 다듬고, 빈 문구는 없음, 100자를 넘으면 자른다. 종류가 없으면 SEARCH")
    void label() {
        assertThat(new SavedPlaceOrigin(null, null, "  민지님 게시물 ", null).label()).isEqualTo("민지님 게시물");
        assertThat(new SavedPlaceOrigin(null, null, " ", null).label()).isNull();
        assertThat(new SavedPlaceOrigin(null, null, "가".repeat(120), null).label()).hasSize(100);
        assertThat(new SavedPlaceOrigin(null, null, null, null).type()).isEqualTo(SavedPlaceSource.SEARCH);
    }
}
