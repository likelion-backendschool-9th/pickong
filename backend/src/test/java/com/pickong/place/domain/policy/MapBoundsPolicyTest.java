package com.pickong.place.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MapBoundsPolicyTest {

    @Test
    @DisplayName("위도 1도는 약 111km다")
    void distance() {
        assertThat(MapBoundsPolicy.distanceMeters(37.0, 127.0, 38.0, 127.0)).isCloseTo(111_195, within(10.0));
    }

    @Test
    @DisplayName("동네 범위(가로 · 세로 수 km)는 허용한다")
    void allowsNeighborhood() {
        assertThat(MapBoundsPolicy.isTooLarge(127.02, 37.49, 127.04, 37.51)).isFalse();
    }

    @Test
    @DisplayName("프론트 한도 15km에 바깥쪽 여유를 더한 범위도 허용한다")
    void allowsFrontendLimitWithMargin() {
        // 세로 15km(약 0.1349도) + 위아래 0.001도씩
        assertThat(MapBoundsPolicy.isTooLarge(127.0, 37.4, 127.05, 37.4 + 0.1349 + 0.002)).isFalse();
    }

    @Test
    @DisplayName("가로나 세로 중 한 변이라도 16km를 넘으면 거절한다")
    void rejectsLargeBounds() {
        assertThat(MapBoundsPolicy.isTooLarge(127.0, 37.4, 127.05, 37.55)).isTrue();
        assertThat(MapBoundsPolicy.isTooLarge(126.8, 37.5, 127.0, 37.51)).isTrue();
        assertThat(MapBoundsPolicy.isTooLarge(126.0, 37.0, 128.0, 38.0)).isTrue();
    }
}
