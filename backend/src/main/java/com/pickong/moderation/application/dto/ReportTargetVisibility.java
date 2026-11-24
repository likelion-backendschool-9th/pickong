package com.pickong.moderation.application.dto;

/** 신고 대상의 현재 공개 상태. 수정 여부와는 따로 둡니다. */
public enum ReportTargetVisibility {
    LIVE,
    HIDDEN,
    DELETED,
    UNAVAILABLE
}
