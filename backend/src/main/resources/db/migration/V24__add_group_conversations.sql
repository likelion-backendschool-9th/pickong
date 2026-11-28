-- 그룹 채팅 (#201): 알림 끄기, 안내 메시지, 그룹 이름 규칙 (backend_blueprint 3장 conversation)
-- 규칙: 방장 없음, 멤버 누구나 이름 변경 · 초대, 내보내기 없음, 본인 포함 3~10명 (인원 규칙은 앱에서 확인)

-- 대화방별 알림 끄기. 알림 기능이 생기기 전까지는 값만 저장합니다.
ALTER TABLE conversation_members ADD COLUMN muted BOOLEAN NOT NULL DEFAULT FALSE;

-- 메시지 종류: TEXT(회원이 보낸 글) | SYSTEM(만들기 · 초대 · 나가기 · 이름 변경 안내)
-- 안내 메시지는 본문 대신 system_payload(누가 · 누구를 · 바뀐 이름, 그때의 아이디)를 씁니다. sender_id는 한 사람입니다.
ALTER TABLE messages ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'TEXT';
ALTER TABLE messages ADD COLUMN system_payload JSONB;
ALTER TABLE messages ADD CONSTRAINT ck_messages_type CHECK (type IN ('TEXT', 'SYSTEM'));
ALTER TABLE messages DROP CONSTRAINT ck_messages_content;
ALTER TABLE messages ADD CONSTRAINT ck_messages_body CHECK (
    (type = 'TEXT' AND system_payload IS NULL AND content IS NOT NULL AND char_length(content) BETWEEN 1 AND 2000)
    OR (type = 'SYSTEM' AND content IS NULL AND system_payload IS NOT NULL)
);

-- 그룹은 이름(1~20자)이 반드시 있음. 1:1은 V23의 ck_conversations_direct_title로 이름이 없음
ALTER TABLE conversations ADD CONSTRAINT ck_conversations_group_title
    CHECK (type = 'DIRECT' OR (title IS NOT NULL AND char_length(title) BETWEEN 1 AND 20));
