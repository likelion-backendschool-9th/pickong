package com.pickong.conversation.application.dto;

import com.pickong.conversation.domain.vo.ConversationType;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 대화 목록 · 상세 한 건 (1:1과 그룹).
 *
 * @param title       그룹 이름. 1:1은 null
 * @param partner     1:1 상대. 그룹은 null
 * @param members     그룹의 참여 중인 사람(나 포함, 들어온 순). 1:1은 빈 목록
 * @param memberCount 참여 중인 사람 수 (1:1은 2)
 * @param lastMessage 내가 볼 수 있는 마지막 메시지(안내 메시지 포함). 없으면 null
 * @param unreadCount 내가 아직 읽지 않은, 다른 사람이 보낸 글 수 (안내 메시지는 세지 않음)
 * @param muted       내 알림 끄기
 * @param updatedAt   마지막 메시지 시각. 메시지가 없으면 내가 참여한 시각
 */
public record ConversationSummaryResult(long id, ConversationType type, String title, ConversationPartner partner,
                                        List<GroupMemberResult> members, int memberCount, MessageResult lastMessage,
                                        long unreadCount, boolean muted, OffsetDateTime createdAt,
                                        OffsetDateTime updatedAt) {
}
