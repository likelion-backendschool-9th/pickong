package com.pickong.feed.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.LoginUser;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시물 작성 · 수정에 업로드한 사진을 연결하는 규칙을 실제 API와 DB(media_files, V25 FK · UNIQUE)로 확인함 (#212)
 * 막혔을 때 feed_photos에 행이 남지 않는지도 봄
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class FeedPhotoAttachIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long me;
    private long other;

    @BeforeEach
    void setUp() {
        me = insertUser();
        other = insertUser();
    }

    @Test
    @DisplayName("내 사진이고 업로드 확인이 끝났으면(UPLOADED, PROCESSED) 보낸 순서대로 연결됨")
    void attachOwnUploadedPhotos() throws Exception {
        long first = media(me, "UPLOADED", "https://cdn.example/1.jpg");
        long second = media(me, "PROCESSED", "https://cdn.example/2.jpg");

        create(me, List.of(second, first)).andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT media_file_id FROM feed_photos WHERE media_file_id IN (?, ?) ORDER BY sort_order",
                Long.class, first, second)).containsExactly(second, first);
    }

    @Test
    @DisplayName("없는 사진 · 남의 사진은 404 MEDIA_NOT_FOUND, 남의 사진은 실패한 사진이어도 404")
    void missingOrOthersPhoto() throws Exception {
        expectError(create(me, List.of(Long.MAX_VALUE)), 404, "MEDIA_NOT_FOUND");
        expectError(create(me, List.of(media(other, "UPLOADED", "https://cdn.example/o.jpg"))), 404, "MEDIA_NOT_FOUND");
        expectError(create(me, List.of(media(other, "FAILED", null))), 404, "MEDIA_NOT_FOUND");
        assertNoFeedOf(me);
    }

    @Test
    @DisplayName("업로드 확인 전이면 400 MEDIA_NOT_UPLOADED, 실패한 사진이면 409 MEDIA_UPLOAD_FAILED")
    void notUploadedOrFailed() throws Exception {
        expectError(create(me, List.of(media(me, "PENDING", null))), 400, "MEDIA_NOT_UPLOADED");
        expectError(create(me, List.of(media(me, "FAILED", null))), 409, "MEDIA_UPLOAD_FAILED");
        assertNoFeedOf(me);
    }

    @Test
    @DisplayName("같은 사진 id 중복은 400 FEED_PHOTO_DUPLICATED, 11장 이상 · null 항목은 요청 검증 400")
    void duplicateTooManyAndNull() throws Exception {
        long photo = media(me, "UPLOADED", "https://cdn.example/d.jpg");
        expectError(create(me, List.of(photo, photo)), 400, "FEED_PHOTO_DUPLICATED");

        List<Long> eleven = LongStream.range(0, 11).mapToObj(i -> media(me, "UPLOADED", "https://cdn.example/e" + i + ".jpg")).toList();
        create(me, eleven).andExpect(status().isBadRequest());
        mvc.perform(as(me, post("/api/v1/feeds")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"본문\",\"visibility\":\"PUBLIC\",\"mediaFileIds\":[null]}"))
                .andExpect(status().isBadRequest());
        assertNoFeedOf(me);
    }

    @Test
    @DisplayName("다른 게시물에 이미 쓴 사진은 409 FEED_PHOTO_IN_USE, 수정할 때 자기 게시물의 사진은 그대로 쓸 수 있음")
    void photoInUse() throws Exception {
        long photo = media(me, "UPLOADED", "https://cdn.example/u.jpg");
        long feed = feedIdOf(create(me, List.of(photo)).andExpect(status().isOk()));

        expectError(create(me, List.of(photo)), 409, "FEED_PHOTO_IN_USE");

        long added = media(me, "UPLOADED", "https://cdn.example/added.jpg");
        update(me, feed, List.of(photo, added)).andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT media_file_id FROM feed_photos WHERE feed_id = ? ORDER BY sort_order", Long.class, feed))
                .containsExactly(photo, added);
    }

    // 제약 위반이 한 번 나면 PostgreSQL이 같은 트랜잭션의 다음 쿼리를 거부해서 제약마다 테스트를 나눔
    @Test
    @DisplayName("DB도 한 사진을 두 게시물에 연결하지 못하게 막음 (UNIQUE, 앱 확인을 동시 요청이 지나쳤을 때 대비)")
    void uniqueMediaPerFeed() {
        long photo = media(me, "UPLOADED", "https://cdn.example/db.jpg");
        jdbc.update("INSERT INTO feed_photos (feed_id, media_file_id, sort_order) VALUES (?, ?, 0)", insertFeed(me), photo);
        long feedB = insertFeed(me);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO feed_photos (feed_id, media_file_id, sort_order) VALUES (?, ?, 0)", feedB, photo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB가 없는 사진 id 연결을 막음 (media_files FK)")
    void mediaForeignKey() {
        long feed = insertFeed(me);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO feed_photos (feed_id, media_file_id, sort_order) VALUES (?, ?, 0)", feed, Long.MAX_VALUE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ResultActions create(long userId, List<Long> mediaIds) throws Exception {
        return mvc.perform(as(userId, post("/api/v1/feeds")).contentType(MediaType.APPLICATION_JSON).content(body(mediaIds)));
    }

    private ResultActions update(long userId, long feedId, List<Long> mediaIds) throws Exception {
        return mvc.perform(as(userId, put("/api/v1/feeds/" + feedId)).contentType(MediaType.APPLICATION_JSON).content(body(mediaIds)));
    }

    private static String body(List<Long> mediaIds) {
        String ids = mediaIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return "{\"content\":\"사진 게시물\",\"visibility\":\"PUBLIC\",\"mediaFileIds\":[" + ids + "]}";
    }

    private static void expectError(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    private static long feedIdOf(ResultActions result) throws Exception {
        return Long.parseLong(com.jayway.jsonpath.JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id").toString());
    }

    // 막힌 요청은 게시물도 사진 연결도 남기지 않아야 됨
    private void assertNoFeedOf(long userId) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feeds WHERE user_id = ?", Integer.class, userId)).isZero();
    }

    private long media(long owner, String status, String url) {
        return jdbc.queryForObject("INSERT INTO media_files (user_id, status, storage_key, url) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, owner, status, "feeds/" + UUID.randomUUID() + ".jpg", url);
    }

    private long insertFeed(long userId) {
        return jdbc.queryForObject("INSERT INTO feeds (user_id, content, visibility) VALUES (?, '본문', 'PUBLIC') RETURNING id",
                Long.class, userId);
    }

    private long insertUser() {
        String username = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long id = jdbc.queryForObject("INSERT INTO users (username, email) VALUES (?, ?) RETURNING id",
                Long.class, username, username + "@example.com");
        // 필수 약관 재동의 필터에 걸리지 않도록 동의해 둠
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, now() FROM terms WHERE is_required = TRUE
                """, id);
        return id;
    }

    private static MockHttpServletRequestBuilder as(long userId, MockHttpServletRequestBuilder request) {
        return request.servletPath("/api/v1").with(csrf()).with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
    }
}
