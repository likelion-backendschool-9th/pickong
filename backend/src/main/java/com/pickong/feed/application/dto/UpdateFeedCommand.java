package com.pickong.feed.application.dto;

import java.util.List;
import lombok.Getter;
import com.pickong.common.domain.Visibility;

@Getter
public class UpdateFeedCommand {
    private final String content;
    private final Visibility visibility;
    private final List<Long> placeIds;
    private final List<Long> mediaFileIds;

    public UpdateFeedCommand(String content, Visibility visibility, List<Long> mediaFileIds, List<Long> placeIds) {
        this.content = content;
        this.visibility = visibility;
        this.mediaFileIds = mediaFileIds;
        this.placeIds = placeIds;
    }

}