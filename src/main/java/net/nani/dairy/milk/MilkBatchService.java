package net.nani.dairy.milk;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.milk.dto.MilkBatchResponse;
import net.nani.dairy.milk.dto.SaveMilkBatchRequest;
import net.nani.dairy.milk.dto.UpdateMilkBatchQcRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MilkBatchService {

    private final MilkBatchRepository repo;

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

    public MilkBatchResponse updateQc(UpdateMilkBatchQcRequest req) {
        var entity = repo.findByDateAndShift(req.getDate(), req.getShift())
                .orElseThrow(() -> new IllegalArgumentException("Milk batch not found for date+shift"));

        if (entity.getQcStatus() == QcStatus.PASS && req.getQcStatus() != QcStatus.PASS) {
            throw new IllegalArgumentException("Batch locked after QC PASS; QC status cannot be changed");
        }

        if (entity.getQcStatus() == req.getQcStatus()) {
            return toResponse(entity);
        }

        entity.setQcStatus(req.getQcStatus());
        return toResponse(repo.save(entity));
    }

    private void validateTotalLiters(double totalLiters) {
        if (Double.isNaN(totalLiters) || Double.isInfinite(totalLiters) || totalLiters < 0) {
            throw new IllegalArgumentException("totalLiters must be a non-negative finite number");
        }
    }

    private String buildId(LocalDate date, Shift shift) {
        return "MB_" + date + "_" + shift + "_" + UUID.randomUUID().toString().substring(0, 8);
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
}
