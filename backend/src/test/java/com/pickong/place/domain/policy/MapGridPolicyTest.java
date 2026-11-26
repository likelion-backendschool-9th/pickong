package com.pickong.place.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MapGridPolicyTest {

    @Test
    @DisplayName("긴 변은 최대 칸 수의 절반에서 최대 칸 수 사이로 나뉜다")
    void cellCount() {
        for (double width : new double[] {0.002, 0.01, 0.0375, 0.1, 1, 30}) {
            double cells = width / MapGridPolicy.cellSize(127, 37.5, 127 + width, 37.5 + width / 100);
            assertThat(cells).isBetween(MapGridPolicy.MAX_CELLS_PER_SIDE / 2.0 - 1e-6, MapGridPolicy.MAX_CELLS_PER_SIDE + 1e-6);
        }
    }

    @Test
    @DisplayName("크기가 같은 범위는 지도를 옮겨도 칸 크기가 같다")
    void stableWhenPanning() {
        double before = MapGridPolicy.cellSize(127.00, 37.49, 127.03, 37.50);
        double after = MapGridPolicy.cellSize(127.01, 37.50, 127.04, 37.51);
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("넓을수록 칸이 크다")
    void growsWithBounds() {
        assertThat(MapGridPolicy.cellSize(126, 37, 128, 38)).isGreaterThan(MapGridPolicy.cellSize(127, 37.5, 127.01, 37.51));
    }

    @Test
    @DisplayName("점이나 전 세계 범위에도 0보다 큰 유한한 값을 돌려준다")
    void extremes() {
        assertThat(MapGridPolicy.cellSize(127, 37, 127, 37)).isPositive().isFinite();
        assertThat(MapGridPolicy.cellSize(-180, -90, 180, 90)).isPositive().isFinite();
    }
}
