package com.pickong.place.adapter.out.persistence;

import com.pickong.common.domain.GeoPoint;
import com.pickong.place.adapter.out.persistence.repository.PlaceSubmissionJpaRepository;
import com.pickong.place.application.port.out.PlaceSubmissionPersistencePort;
import com.pickong.place.domain.model.PlaceSubmission;
import com.pickong.place.domain.vo.PlaceSubmissionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/** 장소 제보 저장소. geography 좌표는 JDBC로 변환하고, 심사 갱신은 대기 상태 조건으로만 실행합니다. */
@Component
public class PlaceSubmissionPersistenceAdapter implements PlaceSubmissionPersistencePort {

    private static final RowMapper<PlaceSubmission> ROW = (rs, rowNum) -> PlaceSubmission.builder()
            .id(rs.getLong("id"))
            .submitterUserId(rs.getLong("submitter_user_id"))
            .name(rs.getString("name"))
            .address(rs.getString("address"))
            .category(rs.getString("category"))
            .description(rs.getString("description"))
            .imageUrl(rs.getString("image_url"))
            .location(new GeoPoint(rs.getDouble("lat"), rs.getDouble("lng")))
            .status(PlaceSubmissionStatus.valueOf(rs.getString("status")))
            .submittedAt(rs.getObject("submitted_at", java.time.OffsetDateTime.class))
            .reviewerAdminId(rs.getObject("reviewer_admin_id", Long.class))
            .reviewedAt(rs.getObject("reviewed_at", java.time.OffsetDateTime.class))
            .rejectReason(rs.getString("reject_reason"))
            .placeId(rs.getObject("place_id", Long.class))
            .build();

    private static final String SELECT = """
            SELECT id, submitter_user_id, name, address, category, description, image_url,
                   ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lng,
                   status, submitted_at, reviewer_admin_id, reviewed_at, reject_reason, place_id
              FROM place_submissions
            """;

    private final JdbcTemplate jdbc;
    private final PlaceSubmissionJpaRepository repository;

    public PlaceSubmissionPersistenceAdapter(JdbcTemplate jdbc, PlaceSubmissionJpaRepository repository) {
        this.jdbc = jdbc;
        this.repository = repository;
    }

    @Override
    public PlaceSubmission save(PlaceSubmission submission) {
        if (submission.getId() != null) {
            throw new IllegalArgumentException("새 제보만 저장합니다. 심사 결과는 updateIfPending을 사용합니다.");
        }
        if (submission.getStatus() != PlaceSubmissionStatus.PENDING) {
            throw new IllegalArgumentException("접수 저장은 대기 상태만 가능합니다.");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO place_submissions (
                    submitter_user_id, name, address, category, description, image_url, location, status, submitted_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, 'PENDING', ?
                )
                RETURNING id
                """, Long.class,
                submission.getSubmitterUserId(), submission.getName(), submission.getAddress(), submission.getCategory(),
                submission.getDescription(), submission.getImageUrl(),
                submission.getLocation().longitude(), submission.getLocation().latitude(),
                submission.getSubmittedAt());
        return submission.toBuilder().id(id).build();
    }

    @Override
    public Optional<PlaceSubmission> findById(long id) {
        List<PlaceSubmission> rows = jdbc.query(SELECT + " WHERE id = ?", ROW, id);
        return rows.stream().findFirst();
    }

    @Override
    public List<PlaceSubmission> findAll() {
        return jdbc.query(SELECT + " ORDER BY submitted_at DESC, id DESC", ROW);
    }

    @Override
    public boolean updateIfPending(PlaceSubmission submission) {
        if (submission.getId() == null) {
            throw new IllegalArgumentException("저장된 제보만 심사 결과를 반영할 수 있습니다.");
        }
        int updated = repository.updateIfPending(submission.getId(), submission.getStatus().name(),
                submission.getReviewerAdminId(), submission.getReviewedAt(), submission.getRejectReason(),
                submission.getPlaceId());
        return updated == 1;
    }
}
