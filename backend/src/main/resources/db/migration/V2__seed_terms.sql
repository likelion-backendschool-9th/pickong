-- 약관 초기 데이터 (1.0). 가입 완료 시 지금 시행 중인 버전으로 동의를 기록합니다.
-- 약관을 바꿀 때는 이 파일을 고치지 않고, 새 버전 행을 넣는 새 마이그레이션(V숫자)을 추가합니다.
-- content_url은 프론트의 약관 화면 경로입니다 (frontend: /terms/[type]).

INSERT INTO terms (type, version, title, content_url, is_required, effective_at) VALUES
    ('SERVICE',   '1.0', '서비스 이용약관',      '/terms/service',   TRUE,  TIMESTAMPTZ '2026-11-01 00:00:00+09'),
    ('PRIVACY',   '1.0', '개인정보 수집 및 이용', '/terms/privacy',   TRUE,  TIMESTAMPTZ '2026-11-01 00:00:00+09'),
    ('MARKETING', '1.0', '마케팅 정보 수신',      '/terms/marketing', FALSE, TIMESTAMPTZ '2026-11-01 00:00:00+09');
