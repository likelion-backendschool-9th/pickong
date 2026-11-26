-- 허용 값만 확장합니다. 기존 사용자의 status/suspended_until은 변경하지 않습니다.
-- Java UserStatus가 BANNED를 읽을 수 있을 때까지 BANNED를 쓰는 기능은 활성화하지 않습니다.
ALTER TABLE users DROP CONSTRAINT ck_users_status;
ALTER TABLE users ADD CONSTRAINT ck_users_status
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED', 'WITHDRAWN_PENDING', 'WITHDRAWN'));

-- status와 suspended_until의 조합 CHECK는 이번에 추가하지 않습니다.
-- 과거 데이터를 별도 점검하고 user/auth의 만료 처리와 모든 쓰기 경로를 맞춘 뒤 추가/검증합니다.
