package com.pickong.place.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

/** IN 절 자리표시와 UUID 컬럼 읽기. 값은 항상 바인딩합니다. */
final class JdbcIn {

    private JdbcIn() {
    }

    static String placeholders(int size) {
        return Collections.nCopies(size, "?").stream().collect(Collectors.joining(","));
    }

    static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID id) {
            return id;
        }
        return UUID.fromString(value.toString());
    }
}
