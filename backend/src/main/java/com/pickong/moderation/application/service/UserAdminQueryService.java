package com.pickong.moderation.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.moderation.application.dto.AdminUserView;
import com.pickong.moderation.application.port.in.UserAdminQueryUseCase;
import com.pickong.moderation.application.port.out.AdminUserQueryPort;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class UserAdminQueryService implements UserAdminQueryUseCase {
    private final AdminUserQueryPort users;
    private final Clock clock;

    public UserAdminQueryService(AdminUserQueryPort users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    public List<AdminUserView> list(String query, String filter) {
        String selected = filter == null || filter.isBlank() ? "ALL" : filter.strip();
        if (!List.of("ALL", "REPORTED", "SANCTIONED").contains(selected)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        String search = query == null ? "" : query.strip();
        return users.findUsers(search, OffsetDateTime.now(clock)).stream()
                .filter(user -> switch (selected) {
                    case "REPORTED" -> user.reportCount() > 0;
                    case "SANCTIONED" -> !user.sanctions().isEmpty();
                    default -> true;
                })
                .sorted(Comparator.comparingLong(AdminUserView::reportCount).reversed()
                        .thenComparing(Comparator.comparingLong(AdminUserView::id).reversed()))
                .toList();
    }
}
