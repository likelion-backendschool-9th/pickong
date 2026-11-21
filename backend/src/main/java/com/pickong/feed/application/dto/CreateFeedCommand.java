package com.pickong.feed.application.dto;

import com.pickong.common.domain.Visibility;
import java.util.List;
import lombok.Getter;

// 피드 생성 요청을 application 계층으로 전달하는 Command 객체
@Getter
public class CreateFeedCommand {

    private final Long userId;
    private final String content;
    private final Visibility visibility;
    private final List<Long> mediaFileIds;
    private final List<Long> placeIds;

    public CreateFeedCommand(Long userId, String content, Visibility visibility,
                             List<Long> mediaFileIds, List<Long> placeIds) {
        this.userId = userId;
        this.content = content;
        this.visibility = visibility;
        this.mediaFileIds = mediaFileIds;
        this.placeIds = placeIds;
    }
}
