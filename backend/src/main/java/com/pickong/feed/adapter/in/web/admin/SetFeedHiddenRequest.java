package com.pickong.feed.adapter.in.web.admin;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import tools.jackson.databind.JsonNode;

/** hidden은 실제 JSON boolean만 받습니다. 숫자·문자열을 상태값으로 변환하지 않습니다. */
public record SetFeedHiddenRequest(JsonNode hidden, JsonNode reason) {
    public boolean hiddenValue() {
        if (hidden == null || !hidden.isBoolean()) throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        return hidden.asBoolean();
    }

    public String reasonValue() {
        if (reason == null || reason.isNull()) return null;
        if (!reason.isString()) throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        return reason.asString();
    }
}
