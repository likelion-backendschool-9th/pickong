package com.pickong.moderation.application.port.in;

import com.pickong.moderation.application.dto.ReportCommand;
import com.pickong.moderation.application.dto.ReportReceipt;

/** 회원 신고 접수. 신고자 id는 호출하는 쪽이 인증된 회원에서 꺼냅니다. */
public interface ReportUseCase {

    ReportReceipt report(ReportCommand command);
}
