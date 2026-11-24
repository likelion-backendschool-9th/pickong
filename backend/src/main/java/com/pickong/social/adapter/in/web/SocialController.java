package com.pickong.social.adapter.in.web;

import com.pickong.social.adapter.in.web.request.FollowRequest;
import com.pickong.social.application.port.in.RelationCommandUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/users/{username}")
public class SocialController {

    private final RelationCommandUseCase relationCommandUseCase;

    public SocialController(RelationCommandUseCase relationCommandUseCase) {
        this.relationCommandUseCase = relationCommandUseCase;
    }

    @PostMapping("/follow")
    @ResponseStatus(HttpStatus.CREATED)
    public void follow(@PathVariable String username,
                       @Valid @RequestBody FollowRequest request) {
        relationCommandUseCase.follow(request.getUserId(), username);
    }

    @DeleteMapping("/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@PathVariable String username,
                         @RequestParam @NotNull @Positive Long userId) {
        relationCommandUseCase.unfollow(userId, username);
    }
}
