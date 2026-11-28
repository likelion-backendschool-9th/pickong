-- saved_places를 ERD(팀 설계)에 맞춥니다. V17은 이전 JPA 엔티티를 기준으로 만들었습니다.
-- backend_blueprint.md 4장 savedplace "DB 맞추기" 표를 따릅니다.
-- V19(collections)가 먼저 있어야 source_collection_id FK를 걸 수 있어 V22로 둡니다.

-- 1. 출처 종류: source → source_type, 값을 ERD 값으로 바꾸고 필수로 만듭니다.
ALTER TABLE saved_places DROP CONSTRAINT ck_saved_places_source_feed;
ALTER TABLE saved_places RENAME COLUMN source TO source_type;
UPDATE saved_places
   SET source_type = CASE source_type
       WHEN 'FEED' THEN 'FEED'
       WHEN 'COLLECTION_SNAPSHOT' THEN 'COLLECTION'
       WHEN 'AI_IMPORT' THEN 'LINK_IMPORT'
       ELSE 'SEARCH'
   END;
ALTER TABLE saved_places
    ALTER COLUMN source_type TYPE VARCHAR(15),
    ALTER COLUMN source_type SET NOT NULL,
    ADD CONSTRAINT ck_saved_places_source_type
        CHECK (source_type IN ('FEED', 'COLLECTION', 'LINK_IMPORT', 'SEARCH', 'CUSTOM', 'PLAN_VISIT')),
    -- source_feed_id는 FEED일 때만 (ERD 제약 ④)
    ADD CONSTRAINT ck_saved_places_source_feed CHECK (source_feed_id IS NULL OR source_type = 'FEED');

-- 2. 출처 문구 · 원본 URL
ALTER TABLE saved_places
    ADD COLUMN source_label VARCHAR(100),
    ADD COLUMN source_url   TEXT;

-- 3. 방문 상태: NOT_INTERESTED를 없앱니다 (ERD는 WANT_TO_GO · VISITED만).
UPDATE saved_places SET visit_status = 'WANT_TO_GO', visited_at = NULL WHERE visit_status NOT IN ('WANT_TO_GO', 'VISITED');
ALTER TABLE saved_places DROP CONSTRAINT ck_saved_places_visit_status;
ALTER TABLE saved_places
    ALTER COLUMN visit_status TYPE VARCHAR(10),
    ADD CONSTRAINT ck_saved_places_visit_status CHECK (visit_status IN ('WANT_TO_GO', 'VISITED'));

-- 4. 메모: 500자까지 (ERD). 더 긴 메모가 있으면 앞 500자만 남깁니다.
ALTER TABLE saved_places ALTER COLUMN memo TYPE VARCHAR(500) USING left(memo, 500);

-- 5. 출처 컬렉션: COLLECTION일 때만 (ERD 제약 ④). 출처 종류가 하나라 게시물 · 컬렉션 중 최대 1개만 채워집니다.
--    출처 게시물과 같이, 컬렉션이 지워져도 저장 기록은 남기고 연결만 끊습니다.
ALTER TABLE saved_places
    ADD COLUMN source_collection_id BIGINT REFERENCES collections (id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_saved_places_source_collection
        CHECK (source_collection_id IS NULL OR source_type = 'COLLECTION');
