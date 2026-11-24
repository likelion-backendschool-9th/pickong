package com.pickong.social.application.port.in;

/** 팔로우·차단 명령 유스케이스. */
public interface RelationCommandUseCase {

    /** followerId가 targetUsername을 팔로우합니다. */
    void follow(Long followerId, String targetUsername);

    /** followerId가 targetUsername을 언팔로우합니다. */
    void unfollow(Long followerId, String targetUsername);
}
