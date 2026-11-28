package com.pickong.conversation.domain.vo;

import java.util.List;
import java.util.UUID;

/**
 * 안내 메시지 내용 ({@code messages.system_payload}). 사람은 그 일이 있었던 때의 아이디로 남깁니다(나중에 아이디를 바꿔도 그대로).
 *
 * @param actor   한 사람 (만든 사람 · 초대한 사람 · 나간 사람 · 이름을 바꾼 사람)
 * @param targets 초대된 사람들 (CREATED · INVITED). 그 밖에는 빈 목록
 * @param title   바뀐 그룹 이름 (CREATED · RENAMED). 그 밖에는 null
 */
public record SystemEvent(Kind event, Participant actor, List<Participant> targets, String title) {

    public enum Kind {
        /** "A님이 B, C님과 채팅방을 만들었어요" */
        CREATED,
        /** "A님이 B님을 초대했어요" */
        INVITED,
        /** "A님이 나갔어요" */
        LEFT,
        /** "A님이 채팅방 이름을 '…'(으)로 바꿨어요" */
        RENAMED
    }

    /** @param id 회원 공개 id (users.public_id) */
    public record Participant(UUID id, String username) {
    }

    public SystemEvent {
        targets = targets == null ? List.of() : List.copyOf(targets);
    }

    public static SystemEvent created(Participant actor, List<Participant> invitees, String title) {
        return new SystemEvent(Kind.CREATED, actor, invitees, title);
    }

    public static SystemEvent invited(Participant actor, List<Participant> invitees) {
        return new SystemEvent(Kind.INVITED, actor, invitees, null);
    }

    public static SystemEvent left(Participant actor) {
        return new SystemEvent(Kind.LEFT, actor, List.of(), null);
    }

    public static SystemEvent renamed(Participant actor, String title) {
        return new SystemEvent(Kind.RENAMED, actor, List.of(), title);
    }
}
