package com.pickong.place.adapter.out.persistence.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickong.TestcontainersConfiguration;
import com.pickong.place.application.dto.PlaceCluster;
import com.pickong.place.application.dto.PlaceMapGroup;
import com.pickong.place.application.dto.PlaceMapItem;
import com.pickong.place.application.dto.PlaceMapQuery;
import com.pickong.place.application.port.out.PlaceMapQueryPort;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 지도 묶음 SQL. PostGIS 컨테이너에서 실행합니다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PlaceMapQueryAdapterTest {

    /** 다른 테스트 데이터와 겹치지 않는 바다 위 범위 */
    private static final PlaceMapQuery AREA = new PlaceMapQuery(130.0, 35.0, 130.1, 35.1, null);

    @Autowired
    PlaceMapQueryPort port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("같은 좌표는 묶고 혼자인 장소는 장소 값을 돌려준다")
    void groupBySpot() {
        insert("가", 130.05, 35.05, "CAFE");
        insert("나", 130.05, 35.05, "SHOP");
        insert("다", 130.06, 35.05, "CAFE");

        List<PlaceMapGroup> groups = port.groupBySpot(AREA, 10);

        assertThat(groups).hasSize(2);
        assertThat(groups).filteredOn(g -> g.single() != null).singleElement()
                .satisfies(g -> assertThat(g.single().name()).isEqualTo("다"));
        assertThat(groups).filteredOn(g -> g.single() == null).singleElement().satisfies(g -> {
            PlaceCluster cluster = g.cluster();
            assertThat(cluster.count()).isEqualTo(2);
            assertThat(cluster.west()).isEqualTo(cluster.east()).isEqualTo(130.05);
            assertThat(cluster.south()).isEqualTo(cluster.north()).isEqualTo(35.05);
        });
        assertThat(port.groupBySpot(AREA, 1)).hasSize(1);
    }

    @Test
    @DisplayName("격자로 묶으면 장소 수가 보존되고 범위가 묶인 장소를 모두 포함한다")
    void groupByGrid() {
        insert("가", 130.011, 35.011, "CAFE");
        insert("나", 130.012, 35.012, "CAFE");
        insert("다", 130.013, 35.011, "CAFE");
        insert("라", 130.09, 35.09, "CAFE");

        List<PlaceMapGroup> groups = port.groupByGrid(AREA, 0.0625);

        assertThat(groups.stream().mapToLong(g -> g.cluster().count()).sum()).isEqualTo(4);
        assertThat(groups).filteredOn(g -> g.single() == null).singleElement().satisfies(g -> {
            assertThat(g.cluster().count()).isEqualTo(3);
            assertThat(g.cluster().west()).isEqualTo(130.011);
            assertThat(g.cluster().east()).isEqualTo(130.013);
            assertThat(g.cluster().south()).isEqualTo(35.011);
            assertThat(g.cluster().north()).isEqualTo(35.012);
        });
        assertThat(groups).filteredOn(g -> g.single() != null).singleElement()
                .satisfies(g -> assertThat(g.single().name()).isEqualTo("라"));
    }

    @Test
    @DisplayName("묶음의 한 점 범위로 그 좌표의 장소만 이름순으로 읽는다")
    void listAtSpot() {
        insert("나", 130.0512345678901, 35.0512345678901, "CAFE");
        insert("가", 130.0512345678901, 35.0512345678901, "SHOP");
        insert("옆집", 130.0512355, 35.0512345678901, "SHOP");
        PlaceCluster spot = port.groupBySpot(AREA, 10).stream()
                .filter(g -> g.single() == null).findFirst().orElseThrow().cluster();

        PlaceMapQuery query = new PlaceMapQuery(spot.west(), spot.south(), spot.east(), spot.north(), null);

        assertThat(port.list(query, 10)).extracting(PlaceMapItem::name).containsExactly("가", "나");
        assertThat(port.list(query, 1)).hasSize(1);
    }

    @Test
    @DisplayName("카테고리로 거른다")
    void category() {
        insert("가", 130.05, 35.05, "CAFE");
        insert("나", 130.06, 35.05, "SHOP");
        PlaceMapQuery cafes = new PlaceMapQuery(AREA.west(), AREA.south(), AREA.east(), AREA.north(), "CAFE");

        assertThat(port.list(cafes, 10)).extracting(PlaceMapItem::name).containsExactly("가");
        assertThat(port.groupBySpot(cafes, 10)).hasSize(1);
        assertThat(port.groupByGrid(cafes, 0.0625)).hasSize(1);
    }

    @Test
    @DisplayName("범위 밖 · 비공개 · 삭제된 장소는 읽지 않는다")
    void hidden() {
        insert("밖", 130.2, 35.05, "CAFE");
        insert("비공개", 130.05, 35.05, "CAFE");
        insert("삭제", 130.06, 35.05, "CAFE");
        jdbc.update("UPDATE places SET visibility = 'PRIVATE' WHERE name = '비공개'");
        jdbc.update("UPDATE places SET deleted_at = now() WHERE name = '삭제'");

        assertThat(port.list(AREA, 10)).isEmpty();
        assertThat(port.groupBySpot(AREA, 10)).isEmpty();
        assertThat(port.groupByGrid(AREA, 0.0625)).isEmpty();
    }

    private void insert(String name, double lng, double lat, String category) {
        jdbc.update("""
                INSERT INTO places (source_type, name, location, category)
                VALUES ('SYSTEM', ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
                """, name, lng, lat, category);
    }
}
