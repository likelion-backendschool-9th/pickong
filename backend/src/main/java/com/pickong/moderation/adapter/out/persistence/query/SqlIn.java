package com.pickong.moderation.adapter.out.persistence.query;

import java.util.Collections;
import java.util.stream.Collectors;

/** IN 절 자리표시. 값은 항상 바인딩합니다. */
final class SqlIn {

    private SqlIn() {
    }

    static String placeholders(int size) {
        return Collections.nCopies(size, "?").stream().collect(Collectors.joining(", "));
    }
}
