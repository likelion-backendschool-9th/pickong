package com.pickong.moderation.adapter.in.web.admin;

import com.pickong.moderation.application.dto.ReportContent;
import com.pickong.moderation.application.dto.ReportPartyView;
import com.pickong.moderation.application.dto.ReportTargetPreview;
import com.pickong.moderation.application.dto.ReportTargetVisibility;
import com.pickong.moderation.application.dto.ReportView;
import com.pickong.moderation.domain.model.Report;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.time.OffsetDateTime;

/**
 * 관리자 신고 목록. 프론트 adminApi의 Report와 같은 필드입니다.
 * 저장 값 FEED는 화면 값 POST로만 바꿉니다.
 */
public record ReportResponse(
        String id,
        String targetType,
        String targetId,
        TargetResponse target,
        MemberResponse reportedUser,
        MemberResponse reporter,
        String reason,
        String detail,
        OffsetDateTime createdAt,
        String status,
        ResolutionResponse resolution) {

    public record TargetResponse(
            String text,
            String imageUrl,
            String link,
            ContentResponse snapshot,
            ContentResponse current,
            Boolean modified,
            ReportTargetVisibility visibility) {
    }

    /** 신고 당시 또는 현재 내용. 저장되지 않은 이미지는 null입니다. */
    public record ContentResponse(String text, String imageUrl) {

        private static ContentResponse from(ReportContent content) {
            if (content == null) {
                return null;
            }
            return new ContentResponse(content.text(), content.imageUrl());
        }
    }

    /** id는 회원 내부 id 문자열입니다. */
    public record MemberResponse(String id, String username, String nickname, String profileImageUrl) {

        private static MemberResponse from(ReportPartyView party) {
            if (party == null) {
                return null;
            }
            return new MemberResponse(Long.toString(party.id()), party.username(), party.nickname(), party.profileImageUrl());
        }
    }

    public record ResolutionResponse(String action, String memo, OffsetDateTime resolvedAt) {
    }

    public static ReportResponse from(ReportView view) {
        Report report = view.report();
        ReportTargetPreview target = view.target();
        return new ReportResponse(
                Long.toString(report.getId()),
                apiTargetType(report.getTargetType()),
                Long.toString(report.getTargetId()),
                new TargetResponse(
                        target.text(),
                        target.imageUrl(),
                        target.link(),
                        ContentResponse.from(target.snapshot()),
                        ContentResponse.from(target.current()),
                        target.modified(),
                        target.visibility()),
                MemberResponse.from(view.reportedUser()),
                MemberResponse.from(view.reporter()),
                report.getReason(),
                report.getDetail() == null ? "" : report.getDetail(),
                report.getCreatedAt(),
                report.getStatus().name(),
                resolution(report));
    }

    /** 도메인의 게시물 이름은 FEED이고, 관리자 화면은 POST로 받습니다. */
    static String apiTargetType(ReportTargetType type) {
        return type == ReportTargetType.FEED ? "POST" : type.name();
    }

    private static ResolutionResponse resolution(Report report) {
        if (report.getResolutionAction() == null || report.getHandledAt() == null) {
            return null;
        }
        String memo = report.getResolutionMemo() == null ? "" : report.getResolutionMemo();
        return new ResolutionResponse(report.getResolutionAction().name(), memo, report.getHandledAt());
    }
}
