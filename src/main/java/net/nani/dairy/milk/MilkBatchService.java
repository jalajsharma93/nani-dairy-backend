package net.nani.dairy.milk;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.milk.dto.MilkBatchResponse;
import net.nani.dairy.milk.dto.MilkBatchQcEvaluationResponse;
import net.nani.dairy.milk.dto.MilkQcOverrideAuditResponse;
import net.nani.dairy.milk.dto.SaveMilkBatchRequest;
import net.nani.dairy.milk.dto.UpdateMilkBatchQcRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MilkBatchService {

    private final MilkBatchRepository repo;
    private final MilkEntryRepository milkEntryRepository;
    private final MilkQcRuleEngine milkQcRuleEngine;
    private final MilkQcOverrideAuditRepository milkQcOverrideAuditRepository;

    public MilkBatchResponse get(LocalDate date, Shift shift) {
        return repo.findByDateAndShift(date, shift)
                .map(this::toResponse)
                .orElse(null);
    }

    public MilkBatchResponse upsert(SaveMilkBatchRequest req) {
        validateTotalLiters(req.getTotalLiters());

        var existing = repo.findByDateAndShift(req.getDate(), req.getShift());
        if (existing.isPresent()) {
            MilkBatchEntity entity = existing.get();

            if (entity.getQcStatus() == QcStatus.PASS
                    && Double.compare(entity.getTotalLiters(), req.getTotalLiters()) != 0) {
                throw new IllegalArgumentException("Batch locked after QC PASS; milk total cannot be edited");
            }

            if (Double.compare(entity.getTotalLiters(), req.getTotalLiters()) == 0) {
                return toResponse(entity);
            }

            entity.setTotalLiters(req.getTotalLiters());
            return toResponse(repo.save(entity));
        }

        MilkBatchEntity entity = MilkBatchEntity.builder()
                .milkBatchId(buildId(req.getDate(), req.getShift()))
                .date(req.getDate())
                .shift(req.getShift())
                .qcStatus(QcStatus.PENDING)
                .totalLiters(req.getTotalLiters())
                .build();

        return toResponse(repo.save(entity));
    }

    public MilkBatchResponse updateQc(
            UpdateMilkBatchQcRequest req,
            String actorUsername,
            String actorRole,
            boolean supervisorActor
    ) {
        var entity = repo.findByDateAndShift(req.getDate(), req.getShift())
                .orElseThrow(() -> new IllegalArgumentException("Milk batch not found for date+shift"));

        if (entity.getQcStatus() == QcStatus.PASS && req.getQcStatus() != QcStatus.PASS) {
            throw new IllegalArgumentException("Batch locked after QC PASS; QC status cannot be changed");
        }

        QcStatus requestedStatus = req.getQcStatus();
        MilkQcRuleEvaluation evaluation = evaluateRules(req.getDate(), req.getShift());
        QcStatus recommendedStatus = evaluation.recommendedQcStatus();
        QcStatus appliedStatus = requestedStatus;

        boolean lessStrictThanRecommended = isLessStrict(requestedStatus, recommendedStatus);
        boolean overrideRequested = Boolean.TRUE.equals(req.getOverrideRecommendedStatus());
        String overrideReason = trimToNull(req.getOverrideReason());
        boolean overrideApproved = false;

        if (lessStrictThanRecommended) {
            if (overrideRequested) {
                if (!supervisorActor) {
                    throw new IllegalArgumentException("Only ADMIN can approve QC override");
                }
                if (overrideReason == null) {
                    throw new IllegalArgumentException("overrideReason is required for QC override");
                }
                overrideApproved = true;
                appliedStatus = requestedStatus;
            } else {
                appliedStatus = recommendedStatus;
            }

            saveOverrideAudit(
                    req.getDate(),
                    req.getShift(),
                    requestedStatus,
                    recommendedStatus,
                    appliedStatus,
                    overrideRequested,
                    overrideApproved,
                    overrideReason,
                    actorUsername,
                    actorRole,
                    evaluation.triggerCodes()
            );
        }

        if (entity.getQcStatus() == appliedStatus) {
            return toResponse(entity);
        }

        entity.setQcStatus(appliedStatus);
        return toResponse(repo.save(entity));
    }

    public MilkBatchQcEvaluationResponse evaluateQc(LocalDate date, Shift shift) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        Shift effectiveShift = shift != null ? shift : Shift.AM;
        MilkQcRuleEvaluation evaluation = evaluateRules(effectiveDate, effectiveShift);
        return toEvaluationResponse(effectiveDate, effectiveShift, evaluation);
    }

    public List<MilkQcOverrideAuditResponse> listOverrideAudits(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate to = dateTo != null ? dateTo : LocalDate.now();
        LocalDate from = dateFrom != null ? dateFrom : to.minusDays(30);
        return milkQcOverrideAuditRepository.findByBatchDateBetweenOrderByCreatedAtDesc(from, to)
                .stream()
                .map(this::toOverrideAuditResponse)
                .toList();
    }

    private void validateTotalLiters(double totalLiters) {
        if (Double.isNaN(totalLiters) || Double.isInfinite(totalLiters) || totalLiters < 0) {
            throw new IllegalArgumentException("totalLiters must be a non-negative finite number");
        }
    }

    private MilkQcRuleEvaluation evaluateRules(LocalDate date, Shift shift) {
        List<MilkEntryEntity> rows = milkEntryRepository.findByDateAndShift(date, shift);
        return milkQcRuleEngine.evaluate(rows);
    }

    private boolean isLessStrict(QcStatus requested, QcStatus recommended) {
        return severity(requested) < severity(recommended);
    }

    private int severity(QcStatus status) {
        if (status == null) {
            return -1;
        }
        return switch (status) {
            case PENDING -> -1;
            case PASS -> 0;
            case HOLD -> 1;
            case REJECT -> 2;
        };
    }

    private void saveOverrideAudit(
            LocalDate date,
            Shift shift,
            QcStatus requestedStatus,
            QcStatus recommendedStatus,
            QcStatus appliedStatus,
            boolean overrideRequested,
            boolean overrideApproved,
            String overrideReason,
            String actorUsername,
            String actorRole,
            List<String> triggerCodes
    ) {
        String triggerCsv = triggerCodes == null || triggerCodes.isEmpty()
                ? null
                : String.join(",", triggerCodes);

        milkQcOverrideAuditRepository.save(
                MilkQcOverrideAuditEntity.builder()
                        .milkQcOverrideAuditId(buildOverrideAuditId())
                        .batchDate(date)
                        .shift(shift)
                        .requestedQcStatus(requestedStatus)
                        .recommendedQcStatus(recommendedStatus)
                        .appliedQcStatus(appliedStatus)
                        .overrideRequested(overrideRequested)
                        .overrideApproved(overrideApproved)
                        .overrideReason(overrideReason)
                        .triggerCodesCsv(triggerCsv)
                        .actorUsername(normalizeActor(actorUsername))
                        .actorRole(normalizeActorRole(actorRole))
                        .createdAt(OffsetDateTime.now())
                        .build()
        );
    }

    private String buildOverrideAuditId() {
        return "MQA_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String buildId(LocalDate date, Shift shift) {
        return "MB_" + date + "_" + shift + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String normalizeActor(String actorUsername) {
        String normalized = trimToNull(actorUsername);
        return normalized == null ? "unknown" : normalized;
    }

    private String normalizeActorRole(String actorRole) {
        String normalized = trimToNull(actorRole);
        return normalized == null ? "UNKNOWN" : normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private MilkBatchResponse toResponse(MilkBatchEntity e) {
        return MilkBatchResponse.builder()
                .milkBatchId(e.getMilkBatchId())
                .date(e.getDate())
                .shift(e.getShift())
                .totalLiters(e.getTotalLiters())
                .qcStatus(e.getQcStatus())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private MilkBatchQcEvaluationResponse toEvaluationResponse(LocalDate date, Shift shift, MilkQcRuleEvaluation e) {
        return MilkBatchQcEvaluationResponse.builder()
                .date(date)
                .shift(shift)
                .recommendedQcStatus(e.recommendedQcStatus())
                .reviewedEntries(e.reviewedEntries())
                .passEntries(e.passEntries())
                .holdEntries(e.holdEntries())
                .rejectEntries(e.rejectEntries())
                .lowFatHoldCount(e.lowFatHoldCount())
                .lowFatRejectCount(e.lowFatRejectCount())
                .lowSnfHoldCount(e.lowSnfHoldCount())
                .lowSnfRejectCount(e.lowSnfRejectCount())
                .highTemperatureHoldCount(e.highTemperatureHoldCount())
                .lactometerOutOfRangeHoldCount(e.lactometerOutOfRangeHoldCount())
                .badSmellHoldCount(e.badSmellHoldCount())
                .abnormalColorHoldCount(e.abnormalColorHoldCount())
                .highAcidityHoldCount(e.highAcidityHoldCount())
                .waterAdulterationRejectCount(e.waterAdulterationRejectCount())
                .antibioticResidueRejectCount(e.antibioticResidueRejectCount())
                .highBacterialCountHoldCount(e.highBacterialCountHoldCount())
                .explicitRejectCount(e.explicitRejectCount())
                .triggerCodes(e.triggerCodes())
                .build();
    }

    private MilkQcOverrideAuditResponse toOverrideAuditResponse(MilkQcOverrideAuditEntity e) {
        return MilkQcOverrideAuditResponse.builder()
                .milkQcOverrideAuditId(e.getMilkQcOverrideAuditId())
                .batchDate(e.getBatchDate())
                .shift(e.getShift())
                .requestedQcStatus(e.getRequestedQcStatus())
                .recommendedQcStatus(e.getRecommendedQcStatus())
                .appliedQcStatus(e.getAppliedQcStatus())
                .overrideRequested(e.isOverrideRequested())
                .overrideApproved(e.isOverrideApproved())
                .overrideReason(e.getOverrideReason())
                .triggerCodesCsv(e.getTriggerCodesCsv())
                .actorUsername(e.getActorUsername())
                .actorRole(e.getActorRole())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
