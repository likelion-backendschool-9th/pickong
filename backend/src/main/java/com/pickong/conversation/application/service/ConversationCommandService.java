package com.pickong.conversation.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.conversation.application.dto.ConversationSummaryResult;
import com.pickong.conversation.application.dto.OpenDirectResult;
import com.pickong.conversation.application.dto.SentMessage;
import com.pickong.conversation.application.dto.UpdateConversationCommand;
import com.pickong.conversation.application.port.in.ConversationCommandUseCase;
import com.pickong.conversation.application.port.out.ConversationPersistencePort;
import com.pickong.conversation.application.port.out.ConversationPersistencePort.SavedDirect;
import com.pickong.conversation.application.port.out.ConversationQueryPort;
import com.pickong.conversation.application.port.out.MessagePersistencePort;
import com.pickong.conversation.application.port.out.MessagePushPort;
import com.pickong.conversation.domain.ConversationErrorCode;
import com.pickong.conversation.domain.model.Conversation;
import com.pickong.conversation.domain.model.Conversation.InvitePlan;
import com.pickong.conversation.domain.model.ConversationMember;
import com.pickong.conversation.domain.model.Message;
import com.pickong.conversation.domain.vo.GroupTitle;
import com.pickong.conversation.domain.vo.SystemEvent;
import com.pickong.conversation.domain.vo.SystemEvent.Participant;
import com.pickong.user.application.dto.UserSummary;
import com.pickong.user.application.port.in.UserQueryUseCase;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1:1 열기 · 그룹 만들기 · 이름 변경 · 알림 끄기 · 초대 · 나가기.
 * <p>
 * 참여 상태를 바꾸는 일(다시 참여 · 초대 · 나가기 · 이름 변경)은 메시지 보내기와 같은 대화방 행 잠금(SELECT … FOR UPDATE)을
 * 거친 뒤 시각을 정합니다. 그래서 참여 시각 경계와 메시지 순서가 어긋나지 않습니다 (blueprint 5.4.4).
 * 그룹의 만들기 · 초대 · 나가기 · 이름 변경은 안내 메시지(SYSTEM)로 남기고 커밋 후 참여자에게 보냅니다.
 * <p>차단한 · 차단당한 상대 확인은 user_blocks 마이그레이션(#179) 이후에 추가합니다.
 */
@Service
@Transactional
public class ConversationCommandService implements ConversationCommandUseCase {

    private final ConversationPersistencePort conversations;
    private final MessagePersistencePort messages;
    private final ConversationQueryPort queries;
    private final MessagePushPort push;
    private final UserQueryUseCase users;
    private final Clock clock;

    public ConversationCommandService(ConversationPersistencePort conversations, MessagePersistencePort messages,
                                      ConversationQueryPort queries, MessagePushPort push, UserQueryUseCase users,
                                      Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.queries = queries;
        this.push = push;
        this.users = users;
        this.clock = clock;
    }

    /**
     * 상대 확인(ACTIVE) → 나 자신 확인(domain) → 없으면 만들기(DB UNIQUE로 하나만) → 이미 있었는데 내가 나갔으면 다시 참여.
     * 상대가 나간 상태여도 상대를 다시 참여시키지 않습니다. 상대는 새 메시지를 받을 때 다시 참여합니다.
     */
    @Override
    public OpenDirectResult openDirect(long userId, String partnerUsername) {
        UserSummary partner = users.getActiveByUsername(partnerUsername);
        SavedDirect saved = conversations.saveDirectIfAbsent(
                Conversation.openDirect(userId, partner.id(), OffsetDateTime.now(clock)));

        if (!saved.created()) {
            ConversationMember me = lock(saved.conversationId()).memberOf(userId)
                    .orElseThrow(() -> new IllegalStateException("1:1 대화방에 참여자 행이 없습니다: " + saved.conversationId()));
            if (!me.isActive()) {
                conversations.rejoin(me.getId(), me.rejoin(OffsetDateTime.now(clock)).getJoinedAt());
            }
        }
        return new OpenDirectResult(summary(userId, saved.conversationId()), saved.created());
    }

    /** 이름 확인 → 초대할 사람 확인(ACTIVE) → 인원 확인(domain) → 저장 → '만들기' 안내 메시지 */
    @Override
    public ConversationSummaryResult createGroup(long userId, String title, List<String> usernames) {
        GroupTitle groupTitle = GroupTitle.of(title);
        List<Long> invitees = resolve(usernames, userId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        Conversation group = Conversation.createGroup(userId, groupTitle, invitees, now);
        long conversationId = conversations.saveGroup(group);

        Participant actor = participant(userId);
        announce(conversationId, userId, SystemEvent.created(actor, participants(invitees), groupTitle.value()), now,
                group.activeMemberIds());
        return summary(userId, conversationId);
    }

    /**
     * 대화방 잠금 → 초대할 사람 나누기(domain: 이미 참여 중이면 뺌, 10명 확인) → 다시 참여 · 새로 참여 → '초대' 안내 메시지.
     * 들어오는 사람은 이전 대화를 전부 보지만, 들어올 때까지의 메시지는 읽은 것으로 둡니다(안 읽은 수가 쌓이지 않게).
     */
    @Override
    public ConversationSummaryResult invite(long userId, long conversationId, List<String> usernames) {
        Conversation conversation = lock(conversationId);
        conversation.ensureGroup();
        conversation.ensureActiveMember(userId);
        List<Long> invitees = resolve(usernames, userId);
        InvitePlan plan = conversation.planInvite(userId, invitees);
        if (plan.isEmpty()) {
            return summary(userId, conversationId);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        Long latest = messages.findLatestVisibleId(conversationId, null).orElse(null);
        for (ConversationMember member : plan.rejoining()) {
            conversations.rejoin(member.getId(), now);
            if (latest != null) {
                conversations.advanceLastRead(member.getId(), latest);
            }
        }
        conversations.addMembers(conversationId, plan.joining(), now, latest);

        List<Long> joined = new ArrayList<>(plan.joining());
        plan.rejoining().forEach(m -> joined.add(m.getUserId()));
        List<Long> recipients = new ArrayList<>(conversation.activeMemberIds());
        recipients.addAll(joined);
        announce(conversationId, userId, SystemEvent.invited(participant(userId), participants(joined)), now, recipients);
        return summary(userId, conversationId);
    }

    /** 대화방 잠금 → 참여 확인 → 이름(그룹만, 바뀌었을 때만 안내 메시지) · 내 알림 끄기 */
    @Override
    public ConversationSummaryResult update(long userId, long conversationId, UpdateConversationCommand command) {
        Conversation conversation = lock(conversationId);
        ConversationMember me = conversation.ensureActiveMember(userId);

        if (command.title() != null) {
            conversation.ensureGroup();
            GroupTitle title = GroupTitle.of(command.title());
            if (!title.value().equals(conversation.getTitle())) {
                OffsetDateTime now = OffsetDateTime.now(clock);
                conversations.rename(conversationId, title.value());
                announce(conversationId, userId, SystemEvent.renamed(participant(userId), title.value()), now,
                        conversation.activeMemberIds());
            }
        }
        if (command.muted() != null && command.muted() != me.isMuted()) {
            conversations.setMuted(me.getId(), command.muted());
        }
        return summary(userId, conversationId);
    }

    /** 대화방 잠금 → 나가기. 그룹이면 남은 사람에게 '나가기' 안내 메시지, 나간 사람의 다른 탭에는 대화방이 바뀌었다고 알립니다. */
    @Override
    public void leave(long userId, long conversationId) {
        Conversation conversation = lock(conversationId);
        ConversationMember me = conversation.ensureActiveMember(userId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        conversations.leave(me.getId(), me.leave(now).getLeftAt());

        if (!conversation.isDirect()) {
            List<Long> remaining = conversation.activeMemberIds().stream().filter(id -> id != userId).toList();
            announce(conversationId, userId, SystemEvent.left(participant(userId)), now, remaining);
        }
        push.pushConversationUpdated(List.of(userId), conversationId);
    }

    /** 안내 메시지를 저장하고(대화방 시각도 갱신) 커밋 후 받는 사람들에게 보냅니다. */
    private void announce(long conversationId, long actorId, SystemEvent event, OffsetDateTime now,
                          Collection<Long> recipients) {
        long id = messages.save(Message.system(conversationId, actorId, event, now));
        conversations.touch(conversationId, now);
        SentMessage sent = queries.findSentMessage(id)
                .orElseThrow(() -> new IllegalStateException("방금 저장한 안내 메시지를 읽지 못했습니다: " + id));
        push.pushSystemMessage(new LinkedHashSet<>(recipients), sent);
    }

    /** 아이디 → 회원 id (ACTIVE만, 404 USER_NOT_FOUND). 나 자신과 중복은 뺍니다. */
    private List<Long> resolve(List<String> usernames, long me) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String username : usernames == null ? List.<String>of() : usernames) {
            if (username == null || username.isBlank()) {
                continue;
            }
            long id = users.getActiveByUsername(username.strip()).id();
            if (id != me) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private Participant participant(long userId) {
        return participants(List.of(userId)).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("회원을 찾지 못했습니다: " + userId));
    }

    private List<Participant> participants(List<Long> userIds) {
        return userIds.isEmpty() ? List.of() : queries.findParticipants(userIds).stream().filter(Objects::nonNull).toList();
    }

    private ConversationSummaryResult summary(long userId, long conversationId) {
        return queries.findSummary(userId, conversationId)
                .orElseThrow(() -> new IllegalStateException("방금 바꾼 대화방을 읽지 못했습니다: " + conversationId));
    }

    /** 참여 상태를 바꿀 때는 메시지 보내기와 같은 대화방 행 잠금(SELECT … FOR UPDATE)을 거칩니다. */
    private Conversation lock(long conversationId) {
        return conversations.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new BusinessException(ConversationErrorCode.CONVERSATION_NOT_FOUND));
    }
}
