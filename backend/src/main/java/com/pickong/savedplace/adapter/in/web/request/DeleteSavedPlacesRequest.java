package com.pickong.savedplace.adapter.in.web.request;

import java.util.List;

/** DELETE /saved-places 본문. 여러 개를 고른 삭제를 한 번에 처리합니다. */
public record DeleteSavedPlacesRequest(List<String> ids) {
}
