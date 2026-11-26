package com.pickong.moderation.application.port.out;

import com.pickong.moderation.domain.model.Sanction;
import java.util.List;
import java.util.Optional;

/** 제재 이력만 소유합니다. 사용자 상태는 user의 공개 Input Port로 변경합니다. */
public interface SanctionPersistencePort {
    /** 호출자의 쓰기 트랜잭션이 필요합니다. 기존 이력 갱신에는 사용하지 않습니다. */
    Sanction save(Sanction sanction);

    Optional<Sanction> findById(long id);

    Optional<Sanction> findBySourceReportId(long sourceReportId);

    /** 시작 시각 내림차순, 같으면 id 내림차순. 종료된 이력도 포함합니다. */
    List<Sanction> findByUserId(long userId);

    /** 만료 미처리 정지와 경고도 포함합니다. 현재 효력은 도메인이 기준 시각으로 판단합니다. */
    List<Sanction> findOpenByUserId(long userId);

    /**
     * 호출자의 트랜잭션 안에서 해당 사용자 소유 미종료 행의 종료 필드만 한 번 갱신합니다.
     * 원래 유형·사유·기간·처리자·원인 신고는 변경하지 않습니다. 없거나 이미 종료되면 false.
     */
    boolean endIfOpen(Sanction ended);
}
