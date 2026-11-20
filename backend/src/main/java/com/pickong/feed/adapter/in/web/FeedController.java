package com.pickong.feed.adapter.in.web;

import com.pickong.feed.application.port.in.FeedUseCase;
import com.pickong.feed.domain.Feed;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/feeds")
public class FeedController {

    private final FeedUseCase feedUseCase;

    public FeedController(FeedUseCase feedUseCase) {
        this.feedUseCase = feedUseCase;
    }

    @GetMapping
    public List<Feed> getLatestFeeds(@RequestParam int limit) {
        return feedUseCase.getLatestFeeds(limit);
    }
}
