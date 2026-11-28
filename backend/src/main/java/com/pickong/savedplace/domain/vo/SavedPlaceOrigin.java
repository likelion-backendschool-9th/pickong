package com.pickong.savedplace.domain.vo;

import java.util.Locale;

/**
 * 처음 저장한 경로. 한 번 정하면 다시 저장해도 바뀌지 않습니다.
 *
 * @param type   출처 종류
 * @param refId  출처의 내부 id. FEED면 게시물, COLLECTION이면 컬렉션 id이고 그 밖에는 없습니다 (없을 수 있음)
 * @param label  화면에 보여 줄 문구. 100자를 넘으면 자릅니다 (ERD source_label VARCHAR(100))
 * @param url    LINK_IMPORT일 때만. 원문 주소 (http · https만)
 */
public record SavedPlaceOrigin(SavedPlaceSource type, Long refId, String label, String url) {

    static final int MAX_LABEL_LENGTH = 100;

    /** ERD 제약 ④처럼 종류에 맞지 않는 값은 지우고, 문구를 다듬습니다. */
    public SavedPlaceOrigin {
        if (type == null) {
            type = SavedPlaceSource.SEARCH;
        }
        if (type != SavedPlaceSource.FEED && type != SavedPlaceSource.COLLECTION) {
            refId = null;
        }
        if (type != SavedPlaceSource.LINK_IMPORT || !isWebUrl(url)) {
            url = null;
        }
        label = normalizeLabel(label);
    }

    /** 출처를 보내지 않았을 때: 검색 · 지도에서 직접 저장 */
    public static SavedPlaceOrigin direct() {
        return new SavedPlaceOrigin(SavedPlaceSource.SEARCH, null, null, null);
    }

    /** 출처 게시물 내부 id. FEED가 아니면 없습니다. */
    public Long feedId() {
        return type == SavedPlaceSource.FEED ? refId : null;
    }

    /** 출처 컬렉션 내부 id. COLLECTION이 아니면 없습니다. */
    public Long collectionId() {
        return type == SavedPlaceSource.COLLECTION ? refId : null;
    }

    private static String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String trimmed = label.strip();
        return trimmed.length() > MAX_LABEL_LENGTH ? trimmed.substring(0, MAX_LABEL_LENGTH) : trimmed;
    }

    /** 링크 가져오기는 원문 URL만 저장합니다. 작업 id 같은 값은 버립니다. */
    private static boolean isWebUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.strip().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}
