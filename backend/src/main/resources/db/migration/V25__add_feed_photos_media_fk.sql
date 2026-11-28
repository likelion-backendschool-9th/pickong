-- feed_photos.media_file_id가 실제 사진(media_files)을 가리키게 하고, 사진 한 장은 게시물 하나에만 연결함 (#212)
-- V4(feed_photos)가 V21(media_files)보다 먼저 만들어져 FK를 걸 수 없었고, 그동안 없는 사진 id도 저장될 수 있었음

-- 1. 없는 사진을 가리키는 연결 행 정리 (사진 업로드가 생기기 전의 개발 · 테스트 데이터)
DELETE FROM feed_photos fp
WHERE NOT EXISTS (SELECT 1 FROM media_files mf WHERE mf.id = fp.media_file_id);

-- 2. 같은 사진이 여러 게시물에 연결된 행은 가장 먼저 연결한 것만 남김 (UNIQUE를 걸기 전 정리)
DELETE FROM feed_photos fp
USING feed_photos earlier
WHERE earlier.media_file_id = fp.media_file_id
  AND earlier.id < fp.id;

-- 3. 사진 기록은 게시물보다 오래 남을 수 있어 사진이 연결된 동안에는 지우지 못하게 함 (RESTRICT)
--    게시물을 지우면 feed_photos 행은 기존 feeds FK의 ON DELETE CASCADE로 지워지고 사진 기록과 R2 파일은 정리 작업(#171)이 지움
ALTER TABLE feed_photos
    ADD CONSTRAINT fk_feed_photos_media_file FOREIGN KEY (media_file_id) REFERENCES media_files (id) ON DELETE RESTRICT;

-- 4. 사진 한 장은 게시물 하나에만. 앱의 확인(FEED_PHOTO_IN_USE)을 동시 요청이 지나쳐도 DB가 한 번 더 막음
--    UNIQUE 제약이 인덱스를 만들어서 "이 사진을 쓰는 게시물이 있는지" 조회에도 쓰임
ALTER TABLE feed_photos
    ADD CONSTRAINT uk_feed_photos_media_file UNIQUE (media_file_id);
