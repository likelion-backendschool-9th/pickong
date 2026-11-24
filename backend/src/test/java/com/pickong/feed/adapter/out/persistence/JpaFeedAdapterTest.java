package com.pickong.feed.adapter.out.persistence;

import com.pickong.feed.adapter.out.persistence.repository.FeedJpaRepository;
import com.pickong.feed.adapter.out.persistence.repository.FeedPhotoJpaRepository;
import com.pickong.feed.adapter.out.persistence.repository.FeedPlaceJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JpaFeedAdapterTest {

    @Mock
    private FeedJpaRepository feedJpaRepository;

    @Mock
    private FeedPhotoJpaRepository feedPhotoJpaRepository;

    @Mock
    private FeedPlaceJpaRepository feedPlaceJpaRepository;

    @InjectMocks
    private JpaFeedAdapter jpaFeedAdapter;

    @Test
    void findRecommendedFeeds_비로그인_PUBLIC만_조회() {
        given(feedJpaRepository.findRecommendedFeeds(null, PageRequest.of(0, 10)))
                .willReturn(List.of());
        jpaFeedAdapter.findRecommendedFeeds(null, null, 10);

        verify(feedJpaRepository).findRecommendedFeeds(null, PageRequest.of(0, 10));
        verify(feedJpaRepository, never()).findRecommendedFeedsForUser(any(), any(), any());
    }

    @Test
    void findRecommendedFeeds_로그인_PUBLIC_FOLLOWERS_조회() {
        given(feedJpaRepository.findRecommendedFeedsForUser(1L, null, PageRequest.of(0, 10)))
                .willReturn(List.of());
        jpaFeedAdapter.findRecommendedFeeds(1L, null, 10);

        // then
        verify(feedJpaRepository).findRecommendedFeedsForUser(1L, null, PageRequest.of(0, 10));
        verify(feedJpaRepository, never()).findRecommendedFeeds(any(), any());
    }
}
