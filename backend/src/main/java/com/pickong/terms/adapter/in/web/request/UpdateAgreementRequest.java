package com.pickong.terms.adapter.in.web.request;

import tools.jackson.databind.JsonNode;

/**
 * PATCH /me/agreements 요청 본문: {@code {"type":"MARKETING","agreed":false}}
 * 종류 검증은 terms 서비스가 하므로 여기서는 받기만 합니다.
 * <p>
 * agreed를 Boolean이 아니라 JsonNode로 받는 이유: Jackson은 Boolean 필드에 숫자 0 · 1을 false · true로 바꿔 넣어서,
 * {@code "agreed":0}이 철회로 처리됩니다. 원래 JSON 값을 받아 true · false인지 직접 확인하면,
 * 앱 전체의 JSON 설정을 바꾸지 않고 이 요청만 mock 계약(boolean이 아니면 400)과 맞출 수 있습니다.
 */
public record UpdateAgreementRequest(String type, JsonNode agreed) {

    /** agreed가 JSON true · false일 때만 그 값. 빠졌거나 null · 숫자 · 문자열이면 null (컨트롤러가 400 INVALID_INPUT) */
    public Boolean agreedValue() {
        return agreed != null && agreed.isBoolean() ? agreed.booleanValue() : null;
    }
}
