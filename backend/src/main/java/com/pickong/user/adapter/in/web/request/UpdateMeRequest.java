package com.pickong.user.adapter.in.web.request;

import com.pickong.user.application.dto.UpdateProfileCommand;
import java.util.Optional;

/**
 * PATCH /me 요청 본문: {@code {"username":"seoyeon","name":null,"bio":"...","region":"서울"}} — 모든 항목은 선택.
 * <p>
 * <b>record가 아니라 setter를 쓰는 이유:</b> "보내지 않음"(그대로 둠)과 "null을 보냄"(지움)을 구분해야 해서입니다.
 * Jackson은 JSON에 있는 항목만 setter를 부르고(값이 null이어도 부름), 없는 항목은 부르지 않습니다.
 * record · Optional로는 둘 다 같은 값이 되어 구분할 수 없습니다(Jackson 3에서 확인).
 */
public class UpdateMeRequest {

    private Optional<String> username;
    private Optional<String> name;
    private Optional<String> bio;
    private Optional<String> region;

    public void setUsername(String username) {
        this.username = Optional.ofNullable(username);
    }

    public void setName(String name) {
        this.name = Optional.ofNullable(name);
    }

    public void setBio(String bio) {
        this.bio = Optional.ofNullable(bio);
    }

    public void setRegion(String region) {
        this.region = Optional.ofNullable(region);
    }

    /** 보내지 않은 항목은 null(그대로 둠), null을 보낸 항목은 Optional.empty()(지움)로 넘깁니다. */
    public UpdateProfileCommand toCommand() {
        return new UpdateProfileCommand(username, name, bio, region);
    }
}
