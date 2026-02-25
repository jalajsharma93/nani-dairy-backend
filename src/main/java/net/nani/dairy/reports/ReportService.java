package net.nani.dairy.reports;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.milk.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final MilkBatchRepository milkBatchRepository;
    private final MilkEntryRepository milkEntryRepository;

    public DailyReportResponse daily(LocalDate date) {
        var am = milkBatchRepository.findByDateAndShift(date, Shift.AM).orElse(null);
        var pm = milkBatchRepository.findByDateAndShift(date, Shift.PM).orElse(null);

        double amLiters = am != null ? am.getTotalLiters() : 0;
        double pmLiters = pm != null ? pm.getTotalLiters() : 0;

        long passBatches = 0;
        long holdBatches = 0;
        long rejectBatches = 0;

        if (am != null) {
            if (am.getQcStatus() == QcStatus.PASS) {
                passBatches++;
            }
            if (am.getQcStatus() == QcStatus.HOLD) {
                holdBatches++;
            }
            if (am.getQcStatus() == QcStatus.REJECT) {
                rejectBatches++;
            }
        }
        if (pm != null) {
            if (pm.getQcStatus() == QcStatus.PASS) {
                passBatches++;
            }
            if (pm.getQcStatus() == QcStatus.HOLD) {
                holdBatches++;
            }
            if (pm.getQcStatus() == QcStatus.REJECT) {
                rejectBatches++;
            }
        }

        var amEntries = milkEntryRepository.findByDateAndShift(date, Shift.AM);
        var pmEntries = milkEntryRepository.findByDateAndShift(date, Shift.PM);

        long cowsQcDone = 0;
        long cowsQcPending = 0;

        for (var e : amEntries) {
            if (e.getQcStatus() == QcStatus.PENDING) {
                cowsQcPending++;
            } else {
                cowsQcDone++;
            }
        }
        for (var e : pmEntries) {
            if (e.getQcStatus() == QcStatus.PENDING) {
                cowsQcPending++;
            } else {
                cowsQcDone++;
            }
        }

        return new DailyReportResponse(
                date,
                amLiters,
                pmLiters,
                amLiters + pmLiters,
                passBatches,
                holdBatches,
                rejectBatches,
                cowsQcDone,
                cowsQcPending
        );
    }

    public WeeklyTrendResponse weekly(LocalDate endDate, int days) {
        int safeDays = Math.max(1, Math.min(30, days));
        LocalDate startDate = endDate.minusDays(safeDays - 1L);

        List<WeeklyTrendPointResponse> points = new ArrayList<>();
        for (int i = 0; i < safeDays; i++) {
            LocalDate date = startDate.plusDays(i);
            DailyReportResponse daily = daily(date);

            long totalBatches = daily.passBatches() + daily.holdBatches() + daily.rejectBatches();
            double passRate = totalBatches > 0 ? (double) daily.passBatches() / totalBatches : 0;

            points.add(new WeeklyTrendPointResponse(
                    date,
                    daily.totalLiters(),
                    daily.passBatches(),
                    totalBatches,
                    passRate
            ));
        }

        return new WeeklyTrendResponse(startDate, endDate, points);
    }
}
