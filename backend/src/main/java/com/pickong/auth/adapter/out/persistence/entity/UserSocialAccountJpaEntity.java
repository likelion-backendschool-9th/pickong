package com.pickong.auth.adapter.out.persistence.entity;

import com.pickong.auth.application.dto.ConnectedSocialAccount;
import com.pickong.auth.application.dto.LinkedSocialAccount;
import com.pickong.auth.domain.vo.SocialProvider;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * user_social_accounts 테이블 JPA 엔티티.
 * 소셜 로그인 연동 정보를 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_social_accounts")
public class UserSocialAccountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 소셜 제공자. 값 목록: KAKAO | GOOGLE. 한 회원에 제공자마다 하나만 연결 (user_id, provider UNIQUE). */
    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    /** 제공자의 고유 사용자 식별자 (카카오 회원번호, Google sub). (provider, provider_id) UNIQUE */
    @Column(name = "provider_id", nullable = false)
    private String providerId;

    /** 이 소셜 계정의 인증된 이메일. 제공자가 주지 않거나 인증되지 않았으면 NULL. */
    @Column(name = "email")
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 새 연결 한 줄을 만듭니다. */
    public static UserSocialAccountJpaEntity link(Long userId, String provider, String providerId, String email, OffsetDateTime now) {
        UserSocialAccountJpaEntity entity = new UserSocialAccountJpaEntity();
        entity.userId = userId;
        entity.provider = provider;
        entity.providerId = providerId;
        entity.email = email;
        entity.createdAt = now;
        return entity;
    }

    /** DB 행을 application dto로 바꿉니다. (이 테이블은 domain 모델 없이 dto로 주고받음) */
    public LinkedSocialAccount toDto() {
        return new LinkedSocialAccount(userId, SocialProvider.valueOf(provider), providerId, email);
    }

    /** 로그인 수단 목록용 dto로 바꿉니다. */
    public ConnectedSocialAccount toConnected() {
        return new ConnectedSocialAccount(id, SocialProvider.valueOf(provider), email, createdAt);
    }
}
