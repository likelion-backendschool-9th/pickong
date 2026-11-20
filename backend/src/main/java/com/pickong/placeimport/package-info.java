/**
 * AI 링크 가져오기 모듈: 링크 수집 → 본문 분석 → LLM 장소 후보 추출 → 장소 검증 작업, 진행 상태 · 취소 · 재시도, 비용 기록.
 * 패키지 이름은 Java 예약어 import를 피해서 placeimport입니다.
 *
 * <p>소유 테이블: import_jobs, import_extraction_runs
 * <p>의존 모듈: place, savedplace
 * <p>설계: backend_blueprint.md 5.3
 */
package com.pickong.placeimport;
