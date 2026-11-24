package com.pickong.place.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.domain.GeoPoint;
import com.pickong.place.application.port.out.PlaceSubmissionPersistencePort;
import com.pickong.place.domain.model.PlaceSubmission;
import com.pickong.place.domain.vo.PlaceSubmissionStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 제보 저장 · 조회와 대기 상태 조건부 갱신. PostGIS 컨테이너에서 실행합니다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PlaceSubmissionPersistenceAdapterTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-24T05:00:00Z");
    private static final GeoPoint LOCATION = new GeoPoint(37.5628, 126.9254);

    @Autowired
    PlaceSubmissionPersistencePort port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("저장한 대기 제보를 좌표와 함께 다시 읽고, 최신 접수 순으로 목록을 돌려준다")
    void saveAndFind() {
        long userId = insertUser("seoyeon");
        PlaceSubmission older = port.save(submission(userId, "먼저", NOW.minusHours(1)));
        PlaceSubmission newer = port.save(submission(userId, "나중", NOW));

        PlaceSubmission found = port.findById(newer.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("나중");
        assertThat(found.getStatus()).isEqualTo(PlaceSubmissionStatus.PENDING);
        assertThat(found.getLocation().latitude()).isEqualTo(LOCATION.latitude());
        assertThat(found.getLocation().longitude()).isEqualTo(LOCATION.longitude());
        assertThat(found.getPlaceId()).isNull();
        assertThat(found.getSubmittedAt().toInstant()).isEqualTo(NOW.toInstant());

        assertThat(port.findAll()).extracting(PlaceSubmission::getId).containsExactly(newer.getId(), older.getId());
    }

    @Test
    @DisplayName("대기 제보를 승인으로 한 번만 바꾸고, 같은 갱신을 다시 하면 실패한다")
    void updateIfPendingOnce() {
        long userId = insertUser("mina");
        PlaceSubmission pending = port.save(submission(userId, "책방", NOW));
        long placeId = insertPlace();

        PlaceSubmission approved = pending.approve(7L, placeId, NOW.plusHours(2));
        assertThat(port.updateIfPending(approved)).isTrue();
        assertThat(port.updateIfPending(approved)).isFalse();

        PlaceSubmission stored = port.findById(pending.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PlaceSubmissionStatus.APPROVED);
        assertThat(stored.getPlaceId()).isEqualTo(placeId);
        assertThat(stored.getReviewerAdminId()).isEqualTo(7L);
        assertThat(stored.getLocation().latitude()).isEqualTo(LOCATION.latitude());
    }

    @Test
    @DisplayName("반려한 뒤에는 같은 제보를 승인으로 바꾸지 못한다")
    void rejectThenApproveDoesNotUpdate() {
        long userId = insertUser("haeun");
        PlaceSubmission pending = port.save(submission(userId, "공원", NOW));
        long placeId = insertPlace();

        assertThat(port.updateIfPending(pending.reject(7L, "위치가 불명확합니다", NOW))).isTrue();
        assertThat(port.updateIfPending(pending.approve(8L, placeId, NOW.plusMinutes(5)))).isFalse();

        PlaceSubmission stored = port.findById(pending.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PlaceSubmissionStatus.REJECTED);
        assertThat(stored.getRejectReason()).isEqualTo("위치가 불명확합니다");
        assertThat(stored.getPlaceId()).isNull();
    }

    private static PlaceSubmission submission(long userId, String name, OffsetDateTime submittedAt) {
        return PlaceSubmission.submit(userId, name, "서울 마포구", "SHOP", "골목", null, LOCATION, submittedAt);
    }

    private long insertUser(String username) {
        return jdbc.queryForObject("INSERT INTO users (username) VALUES (?) RETURNING id", Long.class, username);
    }

    private long insertPlace() {
        return jdbc.queryForObject("""
                INSERT INTO places (name, location, source_type)
                VALUES ('공개 장소', ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, 'CUSTOM')
                RETURNING id
                """, Long.class, LOCATION.longitude(), LOCATION.latitude());
    }
}
