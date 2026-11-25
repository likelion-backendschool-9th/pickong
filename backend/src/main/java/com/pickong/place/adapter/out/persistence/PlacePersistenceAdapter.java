package com.pickong.place.adapter.out.persistence;

import com.pickong.common.domain.GeoPoint;
import com.pickong.common.domain.Visibility;
import com.pickong.place.application.port.out.PlacePersistencePort;
import com.pickong.place.domain.model.Place;
import com.pickong.place.domain.vo.PlaceSourceType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 공개 장소 저장. geography 좌표는 제보 어댑터와 같이 JDBC로 넣습니다. */
@Component
public class PlacePersistenceAdapter implements PlacePersistencePort {

    private final JdbcTemplate jdbc;

    public PlacePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Place save(Place place) {
        if (place.getId() != null) {
            throw new IllegalArgumentException("새 장소만 저장합니다.");
        }
        if (place.getLocation() == null) {
            throw new IllegalArgumentException("장소 좌표가 필요합니다.");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO places (
                    public_id, source_type, visibility, name, address, region, country_code, location,
                    category, description, phone, image_url, hours_source, hours_verified_at,
                    created_at, updated_at, deleted_at, owner_id, image_media_id
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?
                )
                RETURNING id
                """, Long.class,
                place.getPublicId(), place.getSourceType().name(), place.getVisibility().name(), place.getName(),
                place.getAddress(), place.getRegion(), place.getCountryCode(),
                place.getLocation().longitude(), place.getLocation().latitude(),
                place.getCategory(), place.getDescription(), place.getPhone(), place.getImageUrl(),
                place.getHoursSource(), place.getHoursVerifiedAt(),
                place.getCreatedAt(), place.getUpdatedAt(), place.getDeletedAt(),
                place.getOwnerId(), place.getImageMediaId());
        return place.toBuilder().id(id).build();
    }

    @Override
    public Map<Long, UUID> findPublicIdsByIds(Collection<Long> placeIds) {
        if (placeIds == null || placeIds.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT id, public_id FROM places WHERE id IN (" + JdbcIn.placeholders(placeIds.size()) + ")";
        Map<Long, UUID> found = new HashMap<>();
        jdbc.query(sql, (ResultSet rs) -> {
            UUID publicId = JdbcIn.uuid(rs, "public_id");
            if (publicId != null) {
                found.put(rs.getLong("id"), publicId);
            }
        }, placeIds.toArray());
        return found;
    }

    @Override
    public List<Place> findAll() {
        return jdbc.query(SELECT_PUBLIC + " ORDER BY id", (rs, rowNum) -> toPlace(rs));
    }

    @Override
    public List<Place> findInBounds(double west, double south, double east, double north) {
        return jdbc.query(SELECT_PUBLIC + """
                 AND location && ST_MakeEnvelope(?, ?, ?, ?, 4326)::geography
                 ORDER BY id
                """, (rs, rowNum) -> toPlace(rs), west, south, east, north);
    }

    /** 공개 조회용. 비공개 · 삭제된 장소는 읽지 않습니다. */
    private static final String SELECT_PUBLIC = """
            SELECT id, public_id, source_type, visibility, name, address, region, country_code,
                   ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lng,
                   category, description, phone, image_url, hours_source, hours_verified_at,
                   created_at, updated_at, deleted_at, owner_id, image_media_id
              FROM places
             WHERE visibility = 'PUBLIC' AND deleted_at IS NULL
            """;

    private static Place toPlace(ResultSet rs) throws SQLException {
        return Place.builder()
                .id(rs.getLong("id"))
                .publicId(JdbcIn.uuid(rs, "public_id"))
                .sourceType(PlaceSourceType.valueOf(rs.getString("source_type")))
                .visibility(Visibility.valueOf(rs.getString("visibility")))
                .name(rs.getString("name"))
                .address(rs.getString("address"))
                .region(rs.getString("region"))
                .countryCode(rs.getString("country_code"))
                .location(new GeoPoint(rs.getDouble("lat"), rs.getDouble("lng")))
                .category(rs.getString("category"))
                .description(rs.getString("description"))
                .phone(rs.getString("phone"))
                .imageUrl(rs.getString("image_url"))
                .hoursSource(rs.getString("hours_source"))
                .hoursVerifiedAt(rs.getObject("hours_verified_at", OffsetDateTime.class))
                .createdAt(rs.getObject("created_at", OffsetDateTime.class))
                .updatedAt(rs.getObject("updated_at", OffsetDateTime.class))
                .deletedAt(rs.getObject("deleted_at", OffsetDateTime.class))
                .ownerId(rs.getObject("owner_id", Long.class))
                .imageMediaId(rs.getObject("image_media_id", Long.class))
                .build();
    }
}
