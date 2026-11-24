-- 프로필 편집(GET · PATCH /me)에 필요한 회원 컬럼 두 개를 추가합니다.

-- 활동 지역: 자유 입력(예: 서울, 부산 해운대), 20자까지. 정하지 않으면 NULL
ALTER TABLE users ADD COLUMN region VARCHAR(20);

-- 마지막으로 아이디(username)를 바꾼 시각. 아이디는 14일에 한 번만 바꿀 수 있습니다.
-- 가입 후 한 번도 바꾸지 않았으면 NULL (온보딩에서 정한 아이디는 바로 고칠 수 있음)
ALTER TABLE users ADD COLUMN username_changed_at TIMESTAMPTZ;
