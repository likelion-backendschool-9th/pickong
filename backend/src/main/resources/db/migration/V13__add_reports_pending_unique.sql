-- 같은 회원이 같은 대상을 PENDING으로 두 번 넣지 못하게 합니다.
-- RESOLVED 행은 조건에서 빠져서, 처리 뒤에 새 신고 행을 만들 수 있습니다.
-- V7은 이미 적용된 마이그레이션이라 수정하지 않습니다.

CREATE UNIQUE INDEX ux_reports_pending_reporter_target
    ON reports (reporter_id, target_type, target_id)
    WHERE status = 'PENDING';
