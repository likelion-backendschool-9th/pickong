package com.pickong.place.application.dto;

/** 제보 목록에 보여 줄 제보자. id는 회원 공개 id입니다. */
public record SubmitterView(String id, String username, String nickname) {
}
