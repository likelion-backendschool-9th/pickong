package com.pickong.conversation.adapter.in.web;

import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.conversation.adapter.in.web.request.CreateGroupRequest;
import com.pickong.conversation.adapter.in.web.request.InviteRequest;
import com.pickong.conversation.adapter.in.web.request.MarkReadRequest;
import com.pickong.conversation.adapter.in.web.request.OpenDirectRequest;
import com.pickong.conversation.adapter.in.web.request.UpdateConversationRequest;
import com.pickong.conversation.adapter.in.web.response.ConversationResponse;
import com.pickong.conversation.adapter.in.web.response.MessagePageResponse;
import com.pickong.conversation.adapter.in.web.response.UnreadCountResponse;
import com.pickong.conversation.application.dto.MessagePageQuery;
import com.pickong.conversation.application.dto.OpenDirectResult;
import com.pickong.conversation.application.port.in.ConversationCommandUseCase;
import com.pickong.conversation.application.port.in.ConversationQueryUseCase;
import com.pickong.conversation.application.port.in.MessageUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * DM 대화방 HTTP API. 모두 로그인이 필요합니다.
 * <p>
 * 메시지 보내기는 STOMP({@code /app/dm.send})로만 받으므로 HTTP 보내기 API는 없습니다 (blueprint 5.4, common 5.9).
 * 1:1과 그룹이 같은 자원입니다(type DIRECT | GROUP). 그룹은 방장 없이 멤버 누구나 이름 변경 · 초대, 내보내기는 없습니다.
 */
@RestController
@Validated
public class ConversationController {

    private final ConversationQueryUseCase queries;
    private final ConversationCommandUseCase commands;
    private final MessageUseCase messages;

    public ConversationController(ConversationQueryUseCase queries, ConversationCommandUseCase commands,
                                  MessageUseCase messages) {
        this.queries = queries;
        this.commands = commands;
        this.messages = messages;
    }

    /** GET /api/v1/conversations — 내 대화 목록(1:1 · 그룹). 메시지가 있는 대화방만, 마지막 메시지가 최근인 순서 */
    @GetMapping("/conversations")
    public List<ConversationResponse> list(@CurrentUser LoginUser me) {
        return queries.myConversations(me.userId()).stream().map(ConversationResponse::from).toList();
    }

    /** GET /api/v1/conversations/unread-count — 안 읽은 메시지 수 (하단바 배지) */
    @GetMapping("/conversations/unread-count")
    public UnreadCountResponse unreadCount(@CurrentUser LoginUser me) {
        return new UnreadCountResponse(queries.unreadCount(me.userId()));
    }

    /**
     * POST /api/v1/conversations/direct — 1:1 대화방 열기. 새로 만들면 201, 이미 있으면 200.
     * 상대가 없거나 정상(ACTIVE) 회원이 아니면 404 USER_NOT_FOUND, 나 자신이면 400 SELF_CONVERSATION_NOT_ALLOWED
     */
    @PostMapping("/conversations/direct")
    public ResponseEntity<ConversationResponse> openDirect(@CurrentUser LoginUser me,
                                                           @Valid @RequestBody OpenDirectRequest request) {
        OpenDirectResult result = commands.openDirect(me.userId(), request.username());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ConversationResponse.from(result.conversation()));
    }

    /**
     * POST /api/v1/conversations/group — 그룹 만들기 (201). 나 포함 3~10명, 이름 1~20자.
     * 400 INVALID_GROUP_TITLE · GROUP_TOO_SMALL, 409 GROUP_FULL, 404 USER_NOT_FOUND(없거나 ACTIVE가 아닌 아이디)
     */
    @PostMapping("/conversations/group")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse createGroup(@CurrentUser LoginUser me, @Valid @RequestBody CreateGroupRequest request) {
        return ConversationResponse.from(commands.createGroup(me.userId(), request.title(), request.usernames()));
    }

    /**
     * PATCH /api/v1/conversations/{conversationId} — 그룹 이름(멤버 누구나) · 내 알림 끄기. 보낸 항목만 바꿉니다.
     * 1:1에 이름을 보내면 400 NOT_A_GROUP
     */
    @PatchMapping("/conversations/{conversationId}")
    public ConversationResponse update(@CurrentUser LoginUser me, @PathVariable long conversationId,
                                       @RequestBody UpdateConversationRequest request) {
        return ConversationResponse.from(commands.update(me.userId(), conversationId, request.toCommand()));
    }

    /** POST /api/v1/conversations/{conversationId}/members — 그룹 초대(멤버 누구나). 이미 참여 중인 사람은 건너뜀. 409 GROUP_FULL */
    @PostMapping("/conversations/{conversationId}/members")
    public ConversationResponse invite(@CurrentUser LoginUser me, @PathVariable long conversationId,
                                       @Valid @RequestBody InviteRequest request) {
        return ConversationResponse.from(commands.invite(me.userId(), conversationId, request.usernames()));
    }

    /** GET /api/v1/conversations/{conversationId} — 없거나 참여 중이 아니면 404 CONVERSATION_NOT_FOUND */
    @GetMapping("/conversations/{conversationId}")
    public ConversationResponse detail(@CurrentUser LoginUser me, @PathVariable long conversationId) {
        return ConversationResponse.from(queries.getDetail(me.userId(), conversationId));
    }

    /**
     * GET /api/v1/conversations/{conversationId}/messages — 메시지 내역 (오래된 순). 읽음 위치는 바꾸지 않습니다.
     * {@code cursor}: 이 id보다 오래된 메시지, {@code after}: 이 id보다 새 메시지(끊김 복구). 둘 다 보내면 400 INVALID_INPUT
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public MessagePageResponse messages(
            @CurrentUser LoginUser me,
            @PathVariable long conversationId,
            @RequestParam(required = false) @Positive(message = "cursor를 확인해 주세요.") Long cursor,
            @RequestParam(required = false) @Positive(message = "after를 확인해 주세요.") Long after,
            @RequestParam(defaultValue = "30") @Min(value = 1, message = "size는 1 이상이어야 합니다")
            @Max(value = 100, message = "size는 100 이하이어야 합니다") int size) {
        return MessagePageResponse.from(
                queries.messages(me.userId(), conversationId, new MessagePageQuery(cursor, after, size)));
    }

    /**
     * PATCH /api/v1/conversations/{conversationId}/read — 읽음 위치. 본문 {@code {"lastReadMessageId":"1001"}}은 선택이고,
     * 없으면 마지막 메시지까지. 뒤로 가지 않습니다. 이 대화방에서 볼 수 없는 메시지면 404 MESSAGE_NOT_FOUND
     */
    @PatchMapping("/conversations/{conversationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@CurrentUser LoginUser me, @PathVariable long conversationId,
                         @RequestBody(required = false) MarkReadRequest request) {
        messages.markRead(me.userId(), conversationId, request == null ? null : request.messageId());
    }

    /** DELETE /api/v1/conversations/{conversationId}/members/me — 나가기. 참여 중이 아니면 404 CONVERSATION_NOT_FOUND */
    @DeleteMapping("/conversations/{conversationId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@CurrentUser LoginUser me, @PathVariable long conversationId) {
        commands.leave(me.userId(), conversationId);
    }
}
