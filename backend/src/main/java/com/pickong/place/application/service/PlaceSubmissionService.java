package com.pickong.place.application.service;

import com.pickong.common.domain.GeoPoint;
import com.pickong.common.error.BusinessException;
import com.pickong.place.application.dto.PlaceSubmissionView;
import com.pickong.place.application.dto.SubmitPlaceCommand;
import com.pickong.place.application.dto.SubmitterView;
import com.pickong.place.application.port.in.PlaceSubmissionUseCase;
import com.pickong.place.application.port.out.PlacePersistencePort;
import com.pickong.place.application.port.out.PlaceSubmissionPersistencePort;
import com.pickong.place.application.port.out.SubmitterLookupPort;
import com.pickong.place.domain.PlaceErrorCode;
import com.pickong.place.domain.model.Place;
import com.pickong.place.domain.model.PlaceSubmission;
import com.pickong.place.domain.vo.PlaceSourceType;
import com.pickong.place.domain.vo.PlaceSubmissionStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlaceSubmissionService implements PlaceSubmissionUseCase {

    private final PlaceSubmissionPersistencePort submissions;
    private final PlacePersistencePort places;
    private final SubmitterLookupPort submitters;
    private final Clock clock;

    public PlaceSubmissionService(PlaceSubmissionPersistencePort submissions, PlacePersistencePort places,
            SubmitterLookupPort submitters, Clock clock) {
        this.submissions = submissions;
        this.places = places;
        this.submitters = submitters;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PlaceSubmissionView submit(SubmitPlaceCommand command) {
        PlaceSubmission submission = PlaceSubmission.submit(
                command.submitterUserId(),
                command.name(),
                command.address(),
                command.category(),
                command.description(),
                command.imageUrl(),
                coordinates(command.latitude(), command.longitude()),
                OffsetDateTime.now(clock));
        PlaceSubmission saved = submissions.save(submission);
        return new PlaceSubmissionView(saved, submitter(saved.getSubmitterUserId()), null);
    }

    @Override
    public List<PlaceSubmissionView> list() {
        return toViews(submissions.findAll());
    }

    @Override
    public PlaceSubmissionView get(long submissionId) {
        return toViews(List.of(load(submissionId))).getFirst();
    }

    /**
     * 공개 장소를 만든 뒤 대기 제보만 승인으로 바꿉니다.
     * 장소 저장이 실패하거나, 그 사이 다른 관리자가 처리해 조건부 갱신이 실패하면 예외가 나고 트랜잭션이 함께 롤백됩니다.
     */
    @Override
    @Transactional
    public PlaceSubmissionView approve(long submissionId, long reviewerAdminId) {
        PlaceSubmission pending = load(submissionId);
        if (pending.getStatus() != PlaceSubmissionStatus.PENDING) {
            throw new BusinessException(PlaceErrorCode.ALREADY_REVIEWED);
        }
        if (pending.getLocation() == null) {
            throw new BusinessException(PlaceErrorCode.SUBMISSION_NO_LOCATION);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        Place saved = places.save(publicPlace(pending, now));
        PlaceSubmission approved = pending.approve(reviewerAdminId, saved.getId(), now);
        if (!submissions.updateIfPending(approved)) {
            throw new BusinessException(PlaceErrorCode.ALREADY_REVIEWED);
        }
        // 심사 기록은 제보 행에 남깁니다. admin의 AdminActionLogUseCase가 생기면 여기서 그 in-port만 호출합니다.
        return new PlaceSubmissionView(approved, submitter(approved.getSubmitterUserId()), saved.getPublicId());
    }

    @Override
    @Transactional
    public PlaceSubmissionView reject(long submissionId, long reviewerAdminId, String reason) {
        PlaceSubmission pending = load(submissionId);
        PlaceSubmission rejected = pending.reject(reviewerAdminId, reason, OffsetDateTime.now(clock));
        if (!submissions.updateIfPending(rejected)) {
            throw new BusinessException(PlaceErrorCode.ALREADY_REVIEWED);
        }
        // 심사 기록은 제보 행에 남깁니다. admin의 AdminActionLogUseCase가 생기면 여기서 그 in-port만 호출합니다.
        return new PlaceSubmissionView(rejected, submitter(rejected.getSubmitterUserId()), null);
    }

    private SubmitterView submitter(long userId) {
        return submitters.findByUserIds(Set.of(userId)).get(userId);
    }

    private PlaceSubmission load(long submissionId) {
        return submissions.findById(submissionId)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_SUBMISSION_NOT_FOUND));
    }

    private List<PlaceSubmissionView> toViews(List<PlaceSubmission> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, SubmitterView> people = submitters.findByUserIds(
                rows.stream().map(PlaceSubmission::getSubmitterUserId).collect(Collectors.toSet()));
        Set<Long> placeIds = rows.stream().map(PlaceSubmission::getPlaceId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, UUID> publicIds = placeIds.isEmpty() ? Map.of() : places.findPublicIdsByIds(placeIds);
        return rows.stream()
                .map(row -> new PlaceSubmissionView(row, people.get(row.getSubmitterUserId()),
                        row.getPlaceId() == null ? null : publicIds.get(row.getPlaceId())))
                .toList();
    }

    private static Place publicPlace(PlaceSubmission submission, OffsetDateTime now) {
        return Place.builder()
                .publicId(UUID.randomUUID())
                .name(submission.getName())
                .address(submission.getAddress())
                .location(submission.getLocation())
                .category(submission.getCategory())
                .description(submission.getDescription())
                .imageUrl(submission.getImageUrl())
                .sourceType(PlaceSourceType.CUSTOM)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static GeoPoint coordinates(double latitude, double longitude) {
        try {
            return new GeoPoint(latitude, longitude);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(PlaceErrorCode.INVALID_COORDINATES);
        }
    }
}
