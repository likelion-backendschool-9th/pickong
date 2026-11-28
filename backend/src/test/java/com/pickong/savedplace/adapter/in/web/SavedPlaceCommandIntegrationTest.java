package com.pickong.savedplace.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.LoginUser;
import com.pickong.savedplace.domain.event.SavedPlaceDeletingEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST · PATCH · DELETE /saved-places: 실제 보안 필터(CSRF 포함), 서비스, saved_places 쓰기와
 * places · feeds · collections 조회 SQL(SavedPlaceTargetLookupAdapter · SavedPlaceQueryAdapter), V22 제약,
 * 삭제 시 collection 리스너의 구성 장소 정리를 함께 확인합니다.
 * PostGIS 컨테이너가 필요합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@RecordApplicationEvents
class SavedPlaceCommandIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEvents events;

    @Nested
    @DisplayName("POST /saved-places")
    class Save {

        @Test
        @DisplayName("비로그인은 401, CSRF 토큰이 없으면 403")
        void requiresLoginAndCsrf() throws Exception {
            mvc.perform(api(post("/api/v1/saved-places")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            long me = insertUser();
            mvc.perform(api(post("/api/v1/saved-places")).with(authentication(member(me)))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"placeIds\":[]}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("새 장소는 createdPlaceIds, 이미 저장한 장소는 linkedPlaceIds. 중복 id는 하나로 합친다")
        void createsAndLinks() throws Exception {
            long me = insertUser();
            Place already = insertPlace("이미 저장", "PUBLIC", null);
            Place fresh = insertPlace("새로 저장", "PUBLIC", null);
            saveRow(me, already.id());

            mvc.perform(write(post("/api/v1/saved-places"), me, """
                            {"placeIds":["%s","%s","%s"],"collectionIds":[]}
                            """.formatted(already.publicId(), fresh.publicId(), fresh.publicId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.createdPlaceIds", contains(fresh.publicId().toString())))
                    .andExpect(jsonPath("$.linkedPlaceIds", contains(already.publicId().toString())));

            assertThat(count("SELECT count(*) FROM saved_places WHERE user_id = ?", me)).isEqualTo(2);
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT visit_status, memo, source_type FROM saved_places WHERE user_id = ? AND place_id = ?", me, fresh.id());
            assertThat(row).containsEntry("visit_status", "WANT_TO_GO").containsEntry("source_type", "SEARCH");
            assertThat(row.get("memo")).isNull();
        }

        @Test
        @DisplayName("게시물 출처는 FEED로 저장하고 원본 게시물을 연결한다. 응답에서는 POST와 게시물 공개 id로 돌려준다")
        void feedSource() throws Exception {
            long me = insertUser();
            long author = insertUser();
            Place place = insertPlace("게시물 장소", "PUBLIC", null);
            Feed feed = insertFeed(author);
            linkFeedPlace(feed.id(), place.id());

            mvc.perform(write(post("/api/v1/saved-places"), me, """
                            {"placeIds":["%s"],"collectionIds":[],"source":{"type":"POST","refId":"%s","label":"민지님 게시물"}}
                            """.formatted(place.publicId(), feed.publicId())))
                    .andExpect(status().isOk());

            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT source_type, source_feed_id, source_label FROM saved_places WHERE user_id = ?", me);
            assertThat(row).containsEntry("source_type", "FEED").containsEntry("source_feed_id", feed.id())
                    .containsEntry("source_label", "민지님 게시물");
            mvc.perform(api(get("/api/v1/saved-places")).with(authentication(member(me))))
                    .andExpect(jsonPath("$[0].source.type").value("POST"))
                    .andExpect(jsonPath("$[0].source.refId").value(feed.publicId().toString()))
                    .andExpect(jsonPath("$[0].source.label").value("민지님 게시물"));
        }

        @Test
        @DisplayName("컬렉션 출처는 원본 컬렉션을 연결하고, 응답에서 컬렉션 공개 id로 돌려준다")
        void collectionSource() throws Exception {
            long me = insertUser();
            long owner = insertUser();
            Place place = insertPlace("컬렉션 장소", "PUBLIC", null);
            Ref collection = insertCollection(owner);
            addToCollection(collection.id(), saveRow(owner, place.id()), place.id());

            mvc.perform(write(post("/api/v1/saved-places"), me, """
                            {"placeIds":["%s"],"source":{"type":"COLLECTION","refId":"%s","label":"성수 카페 모음"}}
                            """.formatted(place.publicId(), collection.publicId())))
                    .andExpect(status().isOk());

            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT source_type, source_feed_id, source_collection_id FROM saved_places WHERE user_id = ?", me);
            assertThat(row).containsEntry("source_type", "COLLECTION").containsEntry("source_collection_id", collection.id());
            assertThat(row.get("source_feed_id")).isNull();
            mvc.perform(api(get("/api/v1/saved-places")).with(authentication(member(me))))
                    .andExpect(jsonPath("$[0].source.type").value("COLLECTION"))
                    .andExpect(jsonPath("$[0].source.refId").value(collection.publicId().toString()));
        }

        @Test
        @DisplayName("링크 가져오기는 원문 URL만 남기고 작업 id 같은 값은 버린다. 없는 게시물 id는 연결하지 않는다")
        void linkImportAndUnknownFeed() throws Exception {
            long me = insertUser();
            Place url = insertPlace("링크 장소", "PUBLIC", null);
            Place jobId = insertPlace("작업 id 장소", "PUBLIC", null);
            Place unknownFeed = insertPlace("없는 게시물", "PUBLIC", null);

            saveVia(me, url, "{\"type\":\"LINK_IMPORT\",\"refId\":\"https://blog.example.com/post/1\",\"label\":\"블로그\"}");
            saveVia(me, jobId, "{\"type\":\"LINK_IMPORT\",\"refId\":\"job-1001\",\"label\":\"블로그\"}");
            saveVia(me, unknownFeed, "{\"type\":\"POST\",\"refId\":\"%s\"}".formatted(UUID.randomUUID()));

            assertThat(jdbc.queryForObject("SELECT source_url FROM saved_places WHERE place_id = ?", String.class, url.id()))
                    .isEqualTo("https://blog.example.com/post/1");
            assertThat(jdbc.queryForObject("SELECT source_url FROM saved_places WHERE place_id = ?", String.class, jobId.id()))
                    .isNull();
            Map<String, Object> feedRow = jdbc.queryForMap(
                    "SELECT source_type, source_feed_id FROM saved_places WHERE place_id = ?", unknownFeed.id());
            assertThat(feedRow).containsEntry("source_type", "FEED");
            assertThat(feedRow.get("source_feed_id")).isNull();
        }

        @Test
        @DisplayName("이미 저장한 장소를 다시 저장해도 방문 상태 · 메모 · 처음 출처는 그대로다")
        void relinkKeepsState() throws Exception {
            long me = insertUser();
            Place place = insertPlace("다녀온 곳", "PUBLIC", null);
            long saved = saveRow(me, place.id());
            jdbc.update("UPDATE saved_places SET visit_status = 'VISITED', visited_at = now(), memo = '좋았음' WHERE id = ?", saved);

            saveVia(me, place, "{\"type\":\"POST\",\"label\":\"다른 게시물\"}");

            Map<String, Object> row = jdbc.queryForMap("SELECT visit_status, memo, source_type, source_label FROM saved_places WHERE id = ?", saved);
            assertThat(row).containsEntry("visit_status", "VISITED").containsEntry("memo", "좋았음")
                    .containsEntry("source_type", "SEARCH");
            assertThat(row.get("source_label")).isNull();
        }

        @Test
        @DisplayName("빈 목록은 400, 출처 종류가 틀리면 400")
        void badRequests() throws Exception {
            long me = insertUser();
            Place place = insertPlace("장소", "PUBLIC", null);

            mvc.perform(write(post("/api/v1/saved-places"), me, "{\"placeIds\":[]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("NO_PLACE_SELECTED"));
            mvc.perform(write(post("/api/v1/saved-places"), me,
                            "{\"placeIds\":[\"%s\"],\"source\":{\"type\":\"PLAN_VISIT\"}}".formatted(place.publicId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SOURCE"));
            assertThat(count("SELECT count(*) FROM saved_places WHERE user_id = ?", me)).isZero();
        }

        @Test
        @DisplayName("없는 장소 · 삭제된 장소 · 남의 비공개 장소가 하나라도 있으면 404이고 아무것도 저장하지 않는다. 내 비공개 장소는 저장한다")
        void visibility() throws Exception {
            long me = insertUser();
            long other = insertUser();
            Place ok = insertPlace("공개", "PUBLIC", null);
            Place mine = insertPlace("내 비공개", "PRIVATE", me);
            Place othersPrivate = insertPlace("남의 비공개", "PRIVATE", other);
            Place deleted = insertPlace("삭제됨", "PUBLIC", null);
            jdbc.update("UPDATE places SET deleted_at = now() WHERE id = ?", deleted.id());

            for (String bad : List.of(othersPrivate.publicId().toString(), deleted.publicId().toString(),
                    UUID.randomUUID().toString(), "not-a-uuid")) {
                mvc.perform(write(post("/api/v1/saved-places"), me,
                                "{\"placeIds\":[\"%s\",\"%s\"]}".formatted(ok.publicId(), bad)))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
            }
            assertThat(count("SELECT count(*) FROM saved_places WHERE user_id = ?", me)).isZero();

            mvc.perform(write(post("/api/v1/saved-places"), me, "{\"placeIds\":[\"%s\"]}".formatted(mine.publicId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.createdPlaceIds", contains(mine.publicId().toString())));
        }

        private void saveVia(long me, Place place, String sourceJson) throws Exception {
            mvc.perform(write(post("/api/v1/saved-places"), me,
                            "{\"placeIds\":[\"%s\"],\"source\":%s}".formatted(place.publicId(), sourceJson)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("출처 접근 권한 (게시물 · 컬렉션)")
    class SourceAccess {

        @Test
        @DisplayName("볼 수 없는 게시물(남의 비공개, 팔로우하지 않은 팔로워 공개, 숨김)은 출처로 연결하지 않고, 팔로워면 연결한다")
        void feedReadability() throws Exception {
            long me = insertUser();
            long author = insertUser();
            Place privatePlace = insertPlace("비공개 게시물", "PUBLIC", null);
            Place followersPlace = insertPlace("팔로워 게시물", "PUBLIC", null);
            Place hiddenPlace = insertPlace("숨김 게시물", "PUBLIC", null);
            Feed privateFeed = insertFeed(author, "PRIVATE", privatePlace);
            Feed followersFeed = insertFeed(author, "FOLLOWERS", followersPlace);
            Feed hiddenFeed = insertFeed(author, "PUBLIC", hiddenPlace);
            jdbc.update("UPDATE feeds SET hidden_at = now() WHERE id = ?", hiddenFeed.id());

            saveVia(me, privatePlace, feedSource(privateFeed));
            saveVia(me, followersPlace, feedSource(followersFeed));
            saveVia(me, hiddenPlace, feedSource(hiddenFeed));

            assertThat(sourceFeedIdOf(me, privatePlace)).isNull();
            assertThat(sourceFeedIdOf(me, followersPlace)).isNull();
            assertThat(sourceFeedIdOf(me, hiddenPlace)).isNull();
            assertThat(jdbc.queryForList("SELECT source_type FROM saved_places WHERE user_id = ?", String.class, me))
                    .containsOnly("FEED");

            long follower = insertUser();
            jdbc.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", follower, author);
            saveVia(follower, followersPlace, feedSource(followersFeed));
            assertThat(sourceFeedIdOf(follower, followersPlace)).isEqualTo(followersFeed.id());
        }

        @Test
        @DisplayName("출처 게시물에 없는 장소에는 게시물을 연결하지 않는다")
        void placeMustBelongToFeed() throws Exception {
            long me = insertUser();
            Place inFeed = insertPlace("게시물 속 장소", "PUBLIC", null);
            Place notInFeed = insertPlace("다른 장소", "PUBLIC", null);
            Feed feed = insertFeed(insertUser(), "PUBLIC", inFeed);

            mvc.perform(write(post("/api/v1/saved-places"), me, """
                            {"placeIds":["%s","%s"],"source":{"type":"POST","refId":"%s"}}
                            """.formatted(inFeed.publicId(), notInFeed.publicId(), feed.publicId())))
                    .andExpect(status().isOk());

            assertThat(sourceFeedIdOf(me, inFeed)).isEqualTo(feed.id());
            assertThat(sourceFeedIdOf(me, notInFeed)).isNull();
        }

        @Test
        @DisplayName("남의 비공개 컬렉션과 유료 컬렉션은 출처로 연결하지 않는다 (구매 기록이 생기기 전까지)")
        void collectionReadability() throws Exception {
            long me = insertUser();
            long owner = insertUser();
            Place privatePlace = insertPlace("비공개 컬렉션 장소", "PUBLIC", null);
            Place paidPlace = insertPlace("유료 컬렉션 장소", "PUBLIC", null);
            Ref privateCollection = insertCollection(owner);
            Ref paidCollection = insertCollection(owner);
            jdbc.update("UPDATE collections SET visibility = 'PRIVATE' WHERE id = ?", privateCollection.id());
            jdbc.update("UPDATE collections SET price = 3000 WHERE id = ?", paidCollection.id());
            addToCollection(privateCollection.id(), saveRow(owner, privatePlace.id()), privatePlace.id());
            addToCollection(paidCollection.id(), saveRow(owner, paidPlace.id()), paidPlace.id());

            saveVia(me, privatePlace, "{\"type\":\"COLLECTION\",\"refId\":\"%s\"}".formatted(privateCollection.publicId()));
            saveVia(me, paidPlace, "{\"type\":\"COLLECTION\",\"refId\":\"%s\"}".formatted(paidCollection.publicId()));

            assertThat(jdbc.queryForList("SELECT source_collection_id FROM saved_places WHERE user_id = ?", Long.class, me))
                    .hasSize(2).containsOnlyNulls();
        }

        @Test
        @DisplayName("저장 뒤 출처 게시물을 볼 수 없게 되면 refId는 내보내지 않고 출처 종류 · 문구는 남긴다")
        void refIdFollowsCurrentReadability() throws Exception {
            long me = insertUser();
            Place place = insertPlace("공개 범위가 바뀌는 게시물", "PUBLIC", null);
            Feed feed = insertFeed(insertUser(), "PUBLIC", place);
            saveVia(me, place, "{\"type\":\"POST\",\"refId\":\"%s\",\"label\":\"민지님 게시물\"}".formatted(feed.publicId()));

            jdbc.update("UPDATE feeds SET visibility = 'PRIVATE' WHERE id = ?", feed.id());
            mvc.perform(api(get("/api/v1/saved-places")).with(authentication(member(me))))
                    .andExpect(jsonPath("$[0].source.type").value("POST"))
                    .andExpect(jsonPath("$[0].source.refId").value(nullValue()))
                    .andExpect(jsonPath("$[0].source.label").value("민지님 게시물"));

            jdbc.update("UPDATE feeds SET visibility = 'PUBLIC' WHERE id = ?", feed.id());
            mvc.perform(api(get("/api/v1/saved-places")).with(authentication(member(me))))
                    .andExpect(jsonPath("$[0].source.refId").value(feed.publicId().toString()));
        }

        private void saveVia(long me, Place place, String sourceJson) throws Exception {
            mvc.perform(write(post("/api/v1/saved-places"), me,
                            "{\"placeIds\":[\"%s\"],\"source\":%s}".formatted(place.publicId(), sourceJson)))
                    .andExpect(status().isOk());
        }

        private String feedSource(Feed feed) {
            return "{\"type\":\"POST\",\"refId\":\"%s\"}".formatted(feed.publicId());
        }

        private Long sourceFeedIdOf(long userId, Place place) {
            return jdbc.queryForObject("SELECT source_feed_id FROM saved_places WHERE user_id = ? AND place_id = ?",
                    Long.class, userId, place.id());
        }
    }

    @Nested
    @DisplayName("컬렉션에 담기 (아직 지원하지 않음)")
    class CollectionFields {

        @Test
        @DisplayName("collectionIds나 add · set · removeCollectionIds에 값이 있으면 400이고 아무것도 바꾸지 않는다. 빈 목록은 받는다")
        void rejectsUntilSupported() throws Exception {
            long me = insertUser();
            Place place = insertPlace("컬렉션 담기", "PUBLIC", null);

            mvc.perform(write(post("/api/v1/saved-places"), me,
                            "{\"placeIds\":[\"%s\"],\"collectionIds\":[\"c-1\"]}".formatted(place.publicId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COLLECTION_LINK_NOT_SUPPORTED"));
            assertThat(count("SELECT count(*) FROM saved_places WHERE user_id = ?", me)).isZero();

            mvc.perform(write(post("/api/v1/saved-places"), me,
                            "{\"placeIds\":[\"%s\"],\"collectionIds\":[]}".formatted(place.publicId())))
                    .andExpect(status().isOk());
            long saved = jdbc.queryForObject("SELECT id FROM saved_places WHERE user_id = ?", Long.class, me);

            for (String field : List.of("addCollectionIds", "setCollectionIds", "removeCollectionIds")) {
                mvc.perform(write(patch("/api/v1/saved-places"), me,
                                "{\"ids\":[\"%d\"],\"visitStatus\":\"VISITED\",\"%s\":[\"c-1\"]}".formatted(saved, field)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("COLLECTION_LINK_NOT_SUPPORTED"));
            }
            assertThat(jdbc.queryForObject("SELECT visit_status FROM saved_places WHERE id = ?", String.class, saved))
                    .isEqualTo("WANT_TO_GO");
        }
    }

    @Nested
    @DisplayName("PATCH /saved-places")
    class Update {

        @Test
        @DisplayName("방문 상태를 바꾸면 visited_at을 남기고, 되돌리면 지운다. 바뀐 저장 장소를 돌려준다")
        void visitStatus() throws Exception {
            long me = insertUser();
            long a = saveRow(me, insertPlace("가", "PUBLIC", null).id());
            long b = saveRow(me, insertPlace("나", "PUBLIC", null).id());

            mvc.perform(write(patch("/api/v1/saved-places"), me,
                            "{\"ids\":[\"%d\",\"%d\"],\"visitStatus\":\"VISITED\"}".formatted(a, b)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].visitStatus", contains("VISITED", "VISITED")))
                    .andExpect(jsonPath("$[*].id", containsInAnyOrder(String.valueOf(a), String.valueOf(b))));
            assertThat(count("SELECT count(*) FROM saved_places WHERE id IN (?, ?) AND visited_at IS NOT NULL", a, b)).isEqualTo(2);

            mvc.perform(write(patch("/api/v1/saved-places"), me, "{\"ids\":[\"%d\"],\"visitStatus\":\"WANT_TO_GO\"}".formatted(a)))
                    .andExpect(status().isOk());
            Map<String, Object> row = jdbc.queryForMap("SELECT visit_status, visited_at FROM saved_places WHERE id = ?", a);
            assertThat(row).containsEntry("visit_status", "WANT_TO_GO");
            assertThat(row.get("visited_at")).isNull();
        }

        @Test
        @DisplayName("메모는 보낸 때만 바꾸고(생략 · null은 그대로), 빈 문자열은 메모 삭제다. 500자를 넘으면 400")
        void memo() throws Exception {
            long me = insertUser();
            long saved = saveRow(me, insertPlace("메모 장소", "PUBLIC", null).id());

            mvc.perform(write(patch("/api/v1/saved-places"), me, "{\"ids\":[\"%d\"],\"memo\":\"  창가 자리  \"}".formatted(saved)))
                    .andExpect(jsonPath("$[0].memo").value("창가 자리"));
            mvc.perform(write(patch("/api/v1/saved-places"), me, "{\"ids\":[\"%d\"],\"visitStatus\":\"VISITED\"}".formatted(saved)))
                    .andExpect(jsonPath("$[0].memo").value("창가 자리"));
            mvc.perform(write(patch("/api/v1/saved-places"), me, "{\"ids\":[\"%d\"],\"memo\":null}".formatted(saved)))
                    .andExpect(jsonPath("$[0].memo").value("창가 자리"));
            mvc.perform(write(patch("/api/v1/saved-places"), me, "{\"ids\":[\"%d\"],\"memo\":\"\"}".formatted(saved)))
                    .andExpect(jsonPath("$[0].memo").value(nullValue()));
            mvc.perform(write(patch("/api/v1/saved-places"), me,
                            "{\"ids\":[\"%d\"],\"memo\":\"%s\"}".formatted(saved, "가".repeat(501))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MEMO_TOO_LONG"));
        }

        @Test
        @DisplayName("다른 회원의 저장 장소와 없는 id는 건너뛴다. 틀린 방문 상태는 400")
        void othersAndBadStatus() throws Exception {
            long me = insertUser();
            long other = insertUser();
            long mine = saveRow(me, insertPlace("내 것", "PUBLIC", null).id());
            long theirs = saveRow(other, insertPlace("남의 것", "PUBLIC", null).id());

            mvc.perform(write(patch("/api/v1/saved-places"), me,
                            "{\"ids\":[\"%d\",\"%d\",\"999999999\",\"abc\"],\"visitStatus\":\"VISITED\",\"setCollectionIds\":[]}"
                                    .formatted(mine, theirs)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", contains(String.valueOf(mine))));
            assertThat(jdbc.queryForObject("SELECT visit_status FROM saved_places WHERE id = ?", String.class, theirs))
                    .isEqualTo("WANT_TO_GO");

            mvc.perform(write(patch("/api/v1/saved-places"), me, "{\"ids\":[\"%d\"],\"visitStatus\":\"NOT_INTERESTED\"}".formatted(mine)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_VISIT_STATUS"));
        }
    }

    @Nested
    @DisplayName("DELETE /saved-places")
    class Delete {

        @Test
        @DisplayName("본인 저장 기록만 지우고 204. 공용 장소는 남고, 지우기 직전 이벤트를 발행한다")
        void deletesMine() throws Exception {
            long me = insertUser();
            long other = insertUser();
            Place place = insertPlace("장소", "PUBLIC", null);
            long mine = saveRow(me, place.id());
            long theirs = saveRow(other, place.id());

            mvc.perform(write(delete("/api/v1/saved-places"), me, "{\"ids\":[\"%d\",\"%d\",\"x\"]}".formatted(mine, theirs)))
                    .andExpect(status().isNoContent());

            assertThat(count("SELECT count(*) FROM saved_places WHERE id = ?", mine)).isZero();
            assertThat(count("SELECT count(*) FROM saved_places WHERE id = ?", theirs)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM places WHERE id = ?", place.id())).isEqualTo(1);
            assertThat(events.stream(SavedPlaceDeletingEvent.class))
                    .containsExactly(new SavedPlaceDeletingEvent(me, List.of(mine)));
        }

        @Test
        @DisplayName("컬렉션에 담긴 저장 장소도 지워지고, 컬렉션 구성과 장소 수가 함께 정리된다")
        void deletesFromCollections() throws Exception {
            long me = insertUser();
            Place place = insertPlace("담긴 장소", "PUBLIC", null);
            Place kept = insertPlace("남는 장소", "PUBLIC", null);
            long removed = saveRow(me, place.id());
            long remaining = saveRow(me, kept.id());
            Ref collection = insertCollection(me);
            addToCollection(collection.id(), removed, place.id());
            addToCollection(collection.id(), remaining, kept.id());

            mvc.perform(write(delete("/api/v1/saved-places"), me, "{\"ids\":[\"%d\"]}".formatted(removed)))
                    .andExpect(status().isNoContent());

            assertThat(count("SELECT count(*) FROM saved_places WHERE id = ?", removed)).isZero();
            assertThat(count("SELECT count(*) FROM collection_places WHERE collection_id = ?", collection.id())).isEqualTo(1);
            assertThat(count("SELECT place_count FROM collections WHERE id = ?", collection.id())).isEqualTo(1);
        }

        @Test
        @DisplayName("지울 것이 없으면 이벤트 없이 204")
        void nothingToDelete() throws Exception {
            long me = insertUser();

            mvc.perform(write(delete("/api/v1/saved-places"), me, "{\"ids\":[]}"))
                    .andExpect(status().isNoContent());
            assertThat(events.stream(SavedPlaceDeletingEvent.class)).isEmpty();
        }
    }

    @Nested
    @DisplayName("V22 제약 (ERD)")
    class Constraints {

        @Test
        @DisplayName("출처 종류는 필수이고 ERD 값만, 방문 상태는 WANT_TO_GO · VISITED만, 게시물 id는 FEED · 컬렉션 id는 COLLECTION일 때만")
        void checks() {
            long me = insertUser();
            long place = insertPlace("제약", "PUBLIC", null).id();
            long feed = insertFeed(insertUser()).id();
            long collection = insertCollection(insertUser()).id();

            assertRejected("INSERT INTO saved_places (user_id, place_id) VALUES (?, ?)", me, place);
            assertRejected("INSERT INTO saved_places (user_id, place_id, source_type) VALUES (?, ?, 'DIRECT')", me, place);
            assertRejected("INSERT INTO saved_places (user_id, place_id, source_type, visit_status) VALUES (?, ?, 'SEARCH', 'NOT_INTERESTED')", me, place);
            assertRejected("INSERT INTO saved_places (user_id, place_id, source_type, source_feed_id) VALUES (?, ?, 'SEARCH', " + feed + ")", me, place);
            assertRejected("INSERT INTO saved_places (user_id, place_id, source_type, source_collection_id) VALUES (?, ?, 'FEED', " + collection + ")", me, place);
            assertRejected("INSERT INTO saved_places (user_id, place_id, source_type, memo) VALUES (?, ?, 'SEARCH', repeat('a', 501))", me, place);
        }

        private void assertRejected(String sql, long userId, long placeId) {
            // 실패한 문장이 트랜잭션을 깨지 않도록 저장점 안에서 실행합니다.
            jdbc.execute("SAVEPOINT v22");
            assertThatThrownBy(() -> jdbc.update(sql, userId, placeId)).isInstanceOf(DataIntegrityViolationException.class);
            jdbc.execute("ROLLBACK TO SAVEPOINT v22");
        }
    }

    private MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder builder, long userId, String json) {
        return api(builder).with(authentication(member(userId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private record Place(long id, UUID publicId) {
    }

    private record Feed(long id, UUID publicId) {
    }

    private record Ref(long id, UUID publicId) {
    }

    private long insertUser() {
        String username = "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long id = jdbc.queryForObject(
                "INSERT INTO users (username, email) VALUES (?, ?) RETURNING id", Long.class, username, username + "@example.com");
        // 필수 약관 재동의 필터에 걸리지 않도록 동의해 둡니다.
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, now() FROM terms WHERE is_required = TRUE
                """, id);
        return id;
    }

    private Place insertPlace(String name, String visibility, Long ownerId) {
        return jdbc.queryForObject("""
                INSERT INTO places (source_type, name, location, category, visibility, owner_id)
                VALUES (?, ?, ST_SetSRID(ST_MakePoint(127.0, 37.5), 4326)::geography, 'CAFE', ?, ?)
                RETURNING id, public_id
                """, (rs, n) -> new Place(rs.getLong("id"), rs.getObject("public_id", UUID.class)),
                ownerId == null ? "SYSTEM" : "CUSTOM", name, visibility, ownerId);
    }

    private Feed insertFeed(long authorId, String visibility, Place place) {
        Feed feed = jdbc.queryForObject(
                "INSERT INTO feeds (user_id, content, visibility) VALUES (?, 'saved-place-test', ?) RETURNING id, public_id",
                (rs, n) -> new Feed(rs.getLong("id"), rs.getObject("public_id", UUID.class)), authorId, visibility);
        linkFeedPlace(feed.id(), place.id());
        return feed;
    }

    private void linkFeedPlace(long feedId, long placeId) {
        jdbc.update("INSERT INTO feed_places (feed_id, place_id) VALUES (?, ?)", feedId, placeId);
    }

    private Feed insertFeed(long authorId) {
        return jdbc.queryForObject("INSERT INTO feeds (user_id, content) VALUES (?, 'saved-place-test') RETURNING id, public_id",
                (rs, n) -> new Feed(rs.getLong("id"), rs.getObject("public_id", UUID.class)), authorId);
    }

    private Ref insertCollection(long ownerId) {
        return jdbc.queryForObject("INSERT INTO collections (user_id, title) VALUES (?, 'saved-place-test') RETURNING id, public_id",
                (rs, n) -> new Ref(rs.getLong("id"), rs.getObject("public_id", UUID.class)), ownerId);
    }

    private void addToCollection(long collectionId, long savedPlaceId, long placeId) {
        jdbc.update("INSERT INTO collection_places (collection_id, saved_place_id, place_id, sort_order) VALUES (?, ?, ?, 0)",
                collectionId, savedPlaceId, placeId);
        jdbc.update("UPDATE collections SET place_count = place_count + 1 WHERE id = ?", collectionId);
    }

    private long saveRow(long userId, long placeId) {
        return jdbc.queryForObject(
                "INSERT INTO saved_places (user_id, place_id, source_type) VALUES (?, ?, 'SEARCH') RETURNING id", Long.class, userId, placeId);
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private static UsernamePasswordAuthenticationToken member(long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
