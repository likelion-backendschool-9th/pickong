-- places를 Place 도메인 모델에 맞춥니다.
-- V7(신고) · V8(약관)은 다른 브랜치에서 쓰고 있어 V9입니다.

ALTER TABLE places
    ADD COLUMN visibility        VARCHAR(10)  NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN region            VARCHAR(50),
    ADD COLUMN country_code      CHAR(2)      NOT NULL DEFAULT 'KR',
    ADD COLUMN description       VARCHAR(500),
    ADD COLUMN image_url         TEXT,
    ADD COLUMN hours_source      VARCHAR(15),
    ADD COLUMN hours_verified_at TIMESTAMPTZ,
    ADD COLUMN deleted_at        TIMESTAMPTZ,
    -- 직접 만든 장소(CUSTOM)의 등록자. 그 외에는 NULL
    ADD COLUMN owner_id          BIGINT       REFERENCES users (id),
    -- media_files 테이블이 아직 없어 FK를 걸지 않습니다.
    ADD COLUMN image_media_id    BIGINT,
    ADD CONSTRAINT ck_places_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE'));

UPDATE places SET category = 'ETC' WHERE category IS NULL;
ALTER TABLE places
    ALTER COLUMN category SET DEFAULT 'ETC',
    ALTER COLUMN category SET NOT NULL;

-- Place에 없는 값입니다. 외부 id는 place_external_refs가 맡습니다.
ALTER TABLE places
    DROP COLUMN external_id,
    DROP COLUMN website_url;

CREATE INDEX ix_places_owner_id ON places (owner_id);
