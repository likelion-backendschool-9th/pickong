package com.pickong.collection.application.dto;

import com.pickong.user.application.dto.UserSummary;
import java.util.List;

/**
 * 한 회원의 컬렉션 목록. 소유자가 모두 같아서 소유자 정보는 한 번만 담습니다.
 *
 * @param mine 보는 사람이 소유자인지
 */
public record UserCollectionsResult(UserSummary owner, boolean mine, List<CollectionSummary> collections) {
}
