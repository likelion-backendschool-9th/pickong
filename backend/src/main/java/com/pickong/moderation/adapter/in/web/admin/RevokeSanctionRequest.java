package com.pickong.moderation.adapter.in.web.admin;

/** 선택 본문. 생략하거나 사유가 비어 있으면 해제 사유를 저장하지 않습니다. */
public record RevokeSanctionRequest(String reason) {
}
