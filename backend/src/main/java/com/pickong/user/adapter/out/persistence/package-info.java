/**
 * 회원 출력 포트의 JPA 구현(*PersistenceAdapter, *QueryAdapter). 이 모듈이 소유한 테이블에만 씁니다.
 * 다른 모듈 테이블은 QueryAdapter의 읽기 전용 SQL에서만 JOIN합니다 (backend_blueprint.md 6.1).
 */
package com.pickong.user.adapter.out.persistence;
