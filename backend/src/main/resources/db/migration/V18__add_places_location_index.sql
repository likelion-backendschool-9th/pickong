-- 지도 범위 조회(PlaceMapQueryAdapter)용 공간 인덱스.
-- 쿼리가 location::geometry && ST_MakeEnvelope(...)로 비교하므로 같은 식(geometry 캐스팅)에 GiST 인덱스를 만듭니다.
-- geography 컬럼 자체의 인덱스는 이 비교에 쓰이지 않습니다.
CREATE INDEX ix_places_location_geom ON places USING GIST ((location::geometry));
