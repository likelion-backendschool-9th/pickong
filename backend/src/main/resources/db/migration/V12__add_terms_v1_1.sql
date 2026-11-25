-- 약관 1.1: 회원 탈퇴 정책(#103, backend_blueprint.md '회원 탈퇴 결정')을 서비스 이용약관과 개인정보 처리에 넣습니다.
-- 1.0에서 '운영 정책을 따릅니다'로 남겨 둔 탈퇴 · 재가입 · 파기 조항을 실제 정책(30일 유예 후 익명화)으로 채웁니다. (법률 검토 전 초안)
--
-- - SERVICE 1.1: 제1~3조는 1.0과 같고, 제4조(탈퇴)를 구체화하고 제5조(다시 가입)를 추가
-- - PRIVACY 1.1: 수집 항목 · 이용 목적은 1.0과 같고, 보관 기간을 '보관 기간 및 파기'로 구체화
-- - MARKETING은 바뀌지 않아 1.0 그대로
--
-- 재동의 필요 없음(requires_reconsent = false): 수집 항목 · 목적이 그대로이고, 운영 정책으로 미뤄 둔 내용을 구체화한 것이라
-- 공지만 하고 1.0 동의를 그대로 인정합니다. 법률 검토에서 다시 동의가 필요하다고 하면 새 마이그레이션으로 이 값을 바꿉니다.
-- 시행일은 이 마이그레이션을 만든 날(2026-11-25)입니다. 아직 운영 배포 전이라 공지할 회원이 없습니다.
-- 버전 행과 조항은 같은 마이그레이션에 넣습니다(조항 없이 버전만 있으면 전문이 빈 본문으로 보임).

INSERT INTO terms (type, version, title, content_url, is_required, effective_at, requires_reconsent)
SELECT type, '1.1', title, content_url, is_required, TIMESTAMPTZ '2026-11-25 00:00:00+09', FALSE
FROM terms
WHERE type IN ('SERVICE', 'PRIVACY') AND version = '1.0';

-- 1.0과 같은 조항은 그대로 복사합니다 (SERVICE 제1~3조, PRIVACY 수집 항목 · 이용 목적)
INSERT INTO terms_sections (terms_id, position, heading, body)
SELECT next.id, s.position, s.heading, s.body
FROM terms_sections s
JOIN terms prev ON prev.id = s.terms_id AND prev.version = '1.0'
JOIN terms next ON next.type = prev.type AND next.version = '1.1'
WHERE (prev.type = 'SERVICE' AND s.position <= 3)
   OR (prev.type = 'PRIVACY' AND s.position <= 2);

-- 바뀌거나 새로 넣는 조항
INSERT INTO terms_sections (terms_id, position, heading, body)
SELECT t.id, s.position, s.heading, s.body
FROM terms t
JOIN (VALUES
    ('SERVICE', 4, '제4조 (탈퇴)',
     '회원은 언제든지 설정에서 탈퇴를 신청할 수 있습니다. 신청하면 바로 모든 기기에서 로그아웃되고, 프로필과 게시물이 다른 회원에게 보이지 않습니다. 신청한 때부터 30일 안에 같은 소셜 계정으로 다시 로그인하면 탈퇴를 취소할 수 있습니다. 30일이 지나면 계정 정보와 게시물, 저장한 장소 · 컬렉션 · 일정을 지우며 되돌릴 수 없습니다. 회원이 남긴 댓글은 지우지 않고 작성자를 ‘탈퇴한 사용자’로 표시합니다.'),
    ('SERVICE', 5, '제5조 (다시 가입)',
     '탈퇴를 신청한 뒤 30일 동안은 같은 소셜 계정으로 새로 가입할 수 없고, 로그인하면 탈퇴를 취소하는 화면으로 이동합니다. 30일이 지나 계정 정보가 지워진 뒤에는 같은 소셜 계정으로 새로 가입할 수 있으며, 이전 계정의 정보와 게시물은 이어지지 않습니다.'),
    ('PRIVACY', 3, '보관 기간 및 파기',
     '탈퇴를 신청하면 30일 동안 보관한 뒤 파기합니다. 이 기간에 다시 로그인하면 탈퇴를 취소할 수 있습니다. 파기할 때는 이름 · 이메일 · 프로필 사진 · 소개 · 활동 지역 · 소셜 계정 연결을 지우고, 사용자 ID는 알아볼 수 없는 값으로 바꿉니다. 탈퇴 사유는 서비스 개선 통계로만 쓰며, 회원을 알아볼 수 없게 날짜와 함께만 보관합니다. 카카오 · Google 계정 설정의 ‘연결된 앱’에서 찜콩 연결은 회원이 직접 해제할 수 있습니다.')
) AS s (type, position, heading, body) ON s.type = t.type
WHERE t.version = '1.1';
