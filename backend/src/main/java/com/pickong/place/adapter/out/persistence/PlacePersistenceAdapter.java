package com.pickong.place.adapter.out.persistence;

import com.pickong.common.domain.GeoPoint;
import com.pickong.place.application.port.out.PlacePersistencePort;
import com.pickong.place.domain.model.Place;
import com.pickong.place.domain.vo.PlaceSourceType;
import java.sql.ResultSet;
import java.sql.SQLException;
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
                    public_id, name, address, location, category, source_type, external_id, phone, website_url,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, ?, ?, ?, ?
                )
                RETURNING id
                """, Long.class,
                place.getPublicId(), place.getName(), place.getAddress(),
                place.getLocation().longitude(), place.getLocation().latitude(),
                place.getCategory(), place.getSourceType().name(), place.getExternalId(), place.getPhone(),
                place.getWebsiteUrl(), place.getCreatedAt(), place.getUpdatedAt());
        return Place.builder()
                .id(id)
                .publicId(place.getPublicId())
                .name(place.getName())
                .address(place.getAddress())
                .location(place.getLocation())
                .category(place.getCategory())
                .sourceType(place.getSourceType())
                .externalId(place.getExternalId())
                .phone(place.getPhone())
                .websiteUrl(place.getWebsiteUrl())
                .createdAt(place.getCreatedAt())
                .updatedAt(place.getUpdatedAt())
                .build();
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
        return jdbc.query(SELECT + " ORDER BY id", (rs, rowNum) -> toPlace(rs));
    }

    @Override
    public List<Place> findInBounds(double west, double south, double east, double north) {
        return jdbc.query(SELECT + """
                 WHERE location && ST_MakeEnvelope(?, ?, ?, ?, 4326)::geography
                 ORDER BY id
                """, (rs, rowNum) -> toPlace(rs), west, south, east, north);
    }

    private static final String SELECT = """
            SELECT id, public_id, name, address, category, source_type, external_id, phone, website_url,
                   ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lng, created_at, updated_at
              FROM places
            """;

    private static Place toPlace(ResultSet rs) throws SQLException {
        return Place.builder()
                .id(rs.getLong("id"))
                .publicId(JdbcIn.uuid(rs, "public_id"))
                .name(rs.getString("name"))
                .address(rs.getString("address"))
                .location(new GeoPoint(rs.getDouble("lat"), rs.getDouble("lng")))
                .category(rs.getString("category"))
                .sourceType(PlaceSourceType.valueOf(rs.getString("source_type")))
                .externalId(rs.getString("external_id"))
                .phone(rs.getString("phone"))
                .websiteUrl(rs.getString("website_url"))
                .createdAt(rs.getObject("created_at", java.time.OffsetDateTime.class))
                .updatedAt(rs.getObject("updated_at", java.time.OffsetDateTime.class))
                .build();
    }
}
