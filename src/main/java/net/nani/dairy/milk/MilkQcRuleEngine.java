package net.nani.dairy.milk;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class MilkQcRuleEngine {

    static final double FAT_REJECT_BELOW = 2.8;
    static final double FAT_HOLD_BELOW = 3.2;
    static final double SNF_REJECT_BELOW = 7.8;
    static final double SNF_HOLD_BELOW = 8.2;
    static final double TEMPERATURE_HOLD_ABOVE = 38.0;
    static final double LACTOMETER_HOLD_BELOW = 26.0;
    static final double LACTOMETER_HOLD_ABOVE = 34.0;
    static final double ACIDITY_HOLD_BELOW = 0.12;
    static final double ACIDITY_HOLD_ABOVE = 0.18;
    static final double BACTERIAL_HOLD_ABOVE = 1_000_000.0;

    public MilkQcRuleEvaluation evaluate(List<MilkEntryEntity> entries) {
        if (entries == null || entries.isEmpty()) {
            return emptyEvaluation();
        }

        int reviewedEntries = 0;
        int passEntries = 0;
        int holdEntries = 0;
        int rejectEntries = 0;
        int lowFatHoldCount = 0;
        int lowFatRejectCount = 0;
        int lowSnfHoldCount = 0;
        int lowSnfRejectCount = 0;
        int highTemperatureHoldCount = 0;
        int lactometerOutOfRangeHoldCount = 0;
        int badSmellHoldCount = 0;
        int abnormalColorHoldCount = 0;
        int highAcidityHoldCount = 0;
        int waterAdulterationRejectCount = 0;
        int antibioticResidueRejectCount = 0;
        int highBacterialCountHoldCount = 0;
        int explicitRejectCount = 0;

        Set<String> triggerCodes = new LinkedHashSet<>();

        for (MilkEntryEntity entry : entries) {
            if (entry == null) {
                continue;
            }
            CowRuleOutcome outcome = evaluateCow(entry);
            if (!outcome.reviewed()) {
                continue;
            }

            reviewedEntries += 1;
            if (outcome.lowFatHold()) lowFatHoldCount += 1;
            if (outcome.lowFatReject()) lowFatRejectCount += 1;
            if (outcome.lowSnfHold()) lowSnfHoldCount += 1;
            if (outcome.lowSnfReject()) lowSnfRejectCount += 1;
            if (outcome.highTemperatureHold()) highTemperatureHoldCount += 1;
            if (outcome.lactometerOutOfRangeHold()) lactometerOutOfRangeHoldCount += 1;
            if (outcome.badSmellHold()) badSmellHoldCount += 1;
            if (outcome.abnormalColorHold()) abnormalColorHoldCount += 1;
            if (outcome.highAcidityHold()) highAcidityHoldCount += 1;
            if (outcome.waterAdulterationReject()) waterAdulterationRejectCount += 1;
            if (outcome.antibioticResidueReject()) antibioticResidueRejectCount += 1;
            if (outcome.highBacterialCountHold()) highBacterialCountHoldCount += 1;
            if (outcome.explicitReject()) explicitRejectCount += 1;

            triggerCodes.addAll(outcome.triggerCodes());

            if (outcome.status() == QcStatus.REJECT) {
                rejectEntries += 1;
            } else if (outcome.status() == QcStatus.HOLD) {
                holdEntries += 1;
            } else {
                passEntries += 1;
            }
        }

        QcStatus recommended = QcStatus.PENDING;
        if (rejectEntries > 0) {
            recommended = QcStatus.REJECT;
        } else if (holdEntries > 0) {
            recommended = QcStatus.HOLD;
        } else if (reviewedEntries > 0) {
            recommended = QcStatus.PASS;
        }

        return new MilkQcRuleEvaluation(
                recommended,
                reviewedEntries,
                passEntries,
                holdEntries,
                rejectEntries,
                lowFatHoldCount,
                lowFatRejectCount,
                lowSnfHoldCount,
                lowSnfRejectCount,
                highTemperatureHoldCount,
                lactometerOutOfRangeHoldCount,
                badSmellHoldCount,
                abnormalColorHoldCount,
                highAcidityHoldCount,
                waterAdulterationRejectCount,
                antibioticResidueRejectCount,
                highBacterialCountHoldCount,
                explicitRejectCount,
                new ArrayList<>(triggerCodes)
        );
    }

    private CowRuleOutcome evaluateCow(MilkEntryEntity entry) {
        QcStatus status = QcStatus.PASS;
        boolean reviewed = false;
        List<String> triggerCodes = new ArrayList<>();

        boolean lowFatHold = false;
        boolean lowFatReject = false;
        boolean lowSnfHold = false;
        boolean lowSnfReject = false;
        boolean highTemperatureHold = false;
        boolean lactometerOutOfRangeHold = false;
        boolean badSmellHold = false;
        boolean abnormalColorHold = false;
        boolean highAcidityHold = false;
        boolean waterAdulterationReject = false;
        boolean antibioticResidueReject = false;
        boolean highBacterialCountHold = false;
        boolean explicitReject = false;

        String rejectionReason = trimToNull(entry.getRejectionReason());
        if (entry.getQcStatus() == QcStatus.REJECT || rejectionReason != null) {
            status = QcStatus.REJECT;
            explicitReject = true;
            reviewed = true;
            triggerCodes.add("EXPLICIT_REJECT");
            if (rejectionReason != null) {
                triggerCodes.add("REJECTION_REASON_PRESENT");
            }
        } else if (entry.getQcStatus() == QcStatus.HOLD) {
            status = QcStatus.HOLD;
            reviewed = true;
            triggerCodes.add("EXPLICIT_HOLD");
        } else if (entry.getQcStatus() == QcStatus.PASS) {
            status = QcStatus.PASS;
            reviewed = true;
        }

        if (entry.getFat() != null) {
            reviewed = true;
            if (entry.getFat() < FAT_REJECT_BELOW) {
                status = QcStatus.REJECT;
                lowFatReject = true;
                triggerCodes.add("FAT_REJECT");
            } else if (entry.getFat() < FAT_HOLD_BELOW && status != QcStatus.REJECT) {
                status = QcStatus.HOLD;
                lowFatHold = true;
                triggerCodes.add("FAT_HOLD");
            }
        }

        if (entry.getSnf() != null) {
            reviewed = true;
            if (entry.getSnf() < SNF_REJECT_BELOW) {
                status = QcStatus.REJECT;
                lowSnfReject = true;
                triggerCodes.add("SNF_REJECT");
            } else if (entry.getSnf() < SNF_HOLD_BELOW && status != QcStatus.REJECT) {
                status = QcStatus.HOLD;
                lowSnfHold = true;
                triggerCodes.add("SNF_HOLD");
            }
        }

        if (entry.getTemperature() != null) {
            reviewed = true;
            if (entry.getTemperature() > TEMPERATURE_HOLD_ABOVE && status != QcStatus.REJECT) {
                status = QcStatus.HOLD;
                highTemperatureHold = true;
                triggerCodes.add("TEMP_HOLD");
            }
        }

        if (entry.getLactometer() != null) {
            reviewed = true;
            if (
                    (entry.getLactometer() < LACTOMETER_HOLD_BELOW
                            || entry.getLactometer() > LACTOMETER_HOLD_ABOVE)
                            && status != QcStatus.REJECT
            ) {
                status = QcStatus.HOLD;
                lactometerOutOfRangeHold = true;
                triggerCodes.add("LACTOMETER_HOLD");
            }
        }

        String smellNotes = trimToNull(entry.getSmellNotes());
        if (smellNotes != null) {
            reviewed = true;
            String normalized = smellNotes.toLowerCase(Locale.ROOT);
            if (
                    normalized.contains("bad")
                            || normalized.contains("sour")
                            || normalized.contains("foul")
                            || normalized.contains("abnormal")
                            || normalized.contains("off")
            ) {
                if (status != QcStatus.REJECT) {
                    status = QcStatus.HOLD;
                }
                badSmellHold = true;
                triggerCodes.add("SMELL_HOLD");
            }
        }

        String colorObservation = trimToNull(entry.getColorObservation());
        if (colorObservation != null) {
            reviewed = true;
            String normalized = colorObservation.toLowerCase(Locale.ROOT);
            if (
                    normalized.contains("abnormal")
                            || normalized.contains("yellow")
                            || normalized.contains("watery")
                            || normalized.contains("bloody")
                            || normalized.contains("brown")
                            || normalized.contains("red")
            ) {
                if (status != QcStatus.REJECT) {
                    status = QcStatus.HOLD;
                }
                abnormalColorHold = true;
                triggerCodes.add("COLOR_HOLD");
            }
        }

        if (entry.getAcidity() != null) {
            reviewed = true;
            if (
                    (entry.getAcidity() < ACIDITY_HOLD_BELOW || entry.getAcidity() > ACIDITY_HOLD_ABOVE)
                            && status != QcStatus.REJECT
            ) {
                status = QcStatus.HOLD;
                highAcidityHold = true;
                triggerCodes.add("ACIDITY_HOLD");
            }
        }

        if (Boolean.TRUE.equals(entry.getWaterAdulteration())) {
            reviewed = true;
            status = QcStatus.REJECT;
            waterAdulterationReject = true;
            triggerCodes.add("WATER_ADULTERATION_REJECT");
        }

        if (Boolean.TRUE.equals(entry.getAntibioticResidue())) {
            reviewed = true;
            status = QcStatus.REJECT;
            antibioticResidueReject = true;
            triggerCodes.add("ANTIBIOTIC_RESIDUE_REJECT");
        }

        if (entry.getBacterialCount() != null) {
            reviewed = true;
            if (entry.getBacterialCount() > BACTERIAL_HOLD_ABOVE && status != QcStatus.REJECT) {
                status = QcStatus.HOLD;
                highBacterialCountHold = true;
                triggerCodes.add("BACTERIAL_COUNT_HOLD");
            }
        }

        return new CowRuleOutcome(
                reviewed,
                status,
                lowFatHold,
                lowFatReject,
                lowSnfHold,
                lowSnfReject,
                highTemperatureHold,
                lactometerOutOfRangeHold,
                badSmellHold,
                abnormalColorHold,
                highAcidityHold,
                waterAdulterationReject,
                antibioticResidueReject,
                highBacterialCountHold,
                explicitReject,
                triggerCodes
        );
    }

    private MilkQcRuleEvaluation emptyEvaluation() {
        return new MilkQcRuleEvaluation(
                QcStatus.PENDING,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record CowRuleOutcome(
            boolean reviewed,
            QcStatus status,
            boolean lowFatHold,
            boolean lowFatReject,
            boolean lowSnfHold,
            boolean lowSnfReject,
            boolean highTemperatureHold,
            boolean lactometerOutOfRangeHold,
            boolean badSmellHold,
            boolean abnormalColorHold,
            boolean highAcidityHold,
            boolean waterAdulterationReject,
            boolean antibioticResidueReject,
            boolean highBacterialCountHold,
            boolean explicitReject,
            List<String> triggerCodes
    ) {
    }
}
