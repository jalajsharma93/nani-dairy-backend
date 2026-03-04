package net.nani.dairy.milk;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilkQcRuleEngineTest {

    private final MilkQcRuleEngine engine = new MilkQcRuleEngine();

    @Test
    void recommendsRejectWhenFatBelowRejectThreshold() {
        MilkEntryEntity entry = baseEntry();
        entry.setFat(2.6);

        MilkQcRuleEvaluation evaluation = engine.evaluate(List.of(entry));

        assertEquals(QcStatus.REJECT, evaluation.recommendedQcStatus());
        assertEquals(1, evaluation.rejectEntries());
        assertTrue(evaluation.triggerCodes().contains("FAT_REJECT"));
    }

    @Test
    void recommendsHoldWhenTemperatureHigh() {
        MilkEntryEntity entry = baseEntry();
        entry.setFat(3.5);
        entry.setSnf(8.5);
        entry.setTemperature(39.2);

        MilkQcRuleEvaluation evaluation = engine.evaluate(List.of(entry));

        assertEquals(QcStatus.HOLD, evaluation.recommendedQcStatus());
        assertEquals(1, evaluation.holdEntries());
        assertTrue(evaluation.triggerCodes().contains("TEMP_HOLD"));
    }

    @Test
    void recommendsPassWhenMetricsAreHealthy() {
        MilkEntryEntity entry = baseEntry();
        entry.setFat(3.8);
        entry.setSnf(8.7);
        entry.setTemperature(35.5);
        entry.setLactometer(30.0);
        entry.setQcStatus(QcStatus.PASS);

        MilkQcRuleEvaluation evaluation = engine.evaluate(List.of(entry));

        assertEquals(QcStatus.PASS, evaluation.recommendedQcStatus());
        assertEquals(1, evaluation.passEntries());
        assertTrue(evaluation.triggerCodes().isEmpty());
    }

    private MilkEntryEntity baseEntry() {
        return MilkEntryEntity.builder()
                .milkEntryId("ME_TEST_1")
                .date(LocalDate.of(2026, 3, 1))
                .shift(Shift.AM)
                .animalId("AN_TEST_1")
                .liters(5.0)
                .qcStatus(QcStatus.PENDING)
                .build();
    }
}
