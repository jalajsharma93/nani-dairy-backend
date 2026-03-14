package net.nani.dairy.milk;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.milk.dto.MilkEntryResponse;
import net.nani.dairy.milk.dto.SaveMilkEntriesRequest;
import net.nani.dairy.milk.dto.UpdateMilkEntryQcRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MilkEntryService {

    private final MilkEntryRepository repo;
    private final MilkBatchRepository milkBatchRepository;

    public List<MilkEntryResponse> list(LocalDate date, Shift shift) {
        return repo.findByDateAndShift(date, shift).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<MilkEntryResponse> historyByAnimal(String animalId, LocalDate dateFrom, LocalDate dateTo) {
        String normalizedAnimalId = animalId == null ? "" : animalId.trim();
        if (normalizedAnimalId.isEmpty()) {
            throw new IllegalArgumentException("animalId is required");
        }
        if (dateFrom == null || dateTo == null) {
            throw new IllegalArgumentException("dateFrom and dateTo are required");
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo cannot be before dateFrom");
        }

        return repo.findByAnimalIdAndDateBetweenOrderByDateDescCreatedAtDesc(normalizedAnimalId, dateFrom, dateTo)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<MilkEntryResponse> upsertEntries(SaveMilkEntriesRequest req) {
        ensureBatchUnlocked(req.getDate(), req.getShift());

        for (var item : req.getEntries()) {
            validateLiters(item.getLiters());

            var existing = repo.findByDateAndShiftAndAnimalId(req.getDate(), req.getShift(), item.getAnimalId());
            boolean isNew = existing.isEmpty();
            var entity = existing.orElseGet(() -> MilkEntryEntity.builder()
                    .milkEntryId(buildId(req.getDate(), req.getShift(), item.getAnimalId()))
                    .date(req.getDate())
                    .shift(req.getShift())
                    .animalId(item.getAnimalId())
                    .qcStatus(QcStatus.PENDING)
                    .build());

            boolean changed = isNew;
            if (Double.compare(entity.getLiters(), item.getLiters()) != 0) {
                entity.setLiters(item.getLiters());
                changed = true;
            }

            if (changed) {
                repo.save(entity);
            }
        }

        return list(req.getDate(), req.getShift());
    }

    public List<MilkEntryResponse> updateQc(UpdateMilkEntryQcRequest req) {
        ensureBatchUnlocked(req.getDate(), req.getShift());

        for (var item : req.getEntries()) {
            var existing = repo.findByDateAndShiftAndAnimalId(req.getDate(), req.getShift(), item.getAnimalId());
            boolean isNew = existing.isEmpty();
            var entity = existing.orElseGet(() -> MilkEntryEntity.builder()
                    .milkEntryId(buildId(req.getDate(), req.getShift(), item.getAnimalId()))
                    .date(req.getDate())
                    .shift(req.getShift())
                    .animalId(item.getAnimalId())
                    .liters(0)
                    .qcStatus(QcStatus.PENDING)
                    .build());

            boolean changed = isNew;

            if (entity.getQcStatus() != item.getQcStatus()) {
                entity.setQcStatus(item.getQcStatus());
                changed = true;
            }

            if (!Objects.equals(entity.getFat(), item.getFat())) {
                entity.setFat(item.getFat());
                changed = true;
            }
            if (!Objects.equals(entity.getSnf(), item.getSnf())) {
                entity.setSnf(item.getSnf());
                changed = true;
            }
            if (!Objects.equals(entity.getTemperature(), item.getTemperature())) {
                entity.setTemperature(item.getTemperature());
                changed = true;
            }
            if (!Objects.equals(entity.getLactometer(), item.getLactometer())) {
                entity.setLactometer(item.getLactometer());
                changed = true;
            }

            validateNonNegativeMetric(item.getAcidity(), "acidity");
            validateNonNegativeMetric(item.getBacterialCount(), "bacterialCount");
            String colorObservation = normalizeColorObservation(item.getColorObservation());
            if (!Objects.equals(entity.getColorObservation(), colorObservation)) {
                entity.setColorObservation(colorObservation);
                changed = true;
            }
            if (!Objects.equals(entity.getAcidity(), item.getAcidity())) {
                entity.setAcidity(item.getAcidity());
                changed = true;
            }
            if (!Objects.equals(entity.getWaterAdulteration(), item.getWaterAdulteration())) {
                entity.setWaterAdulteration(item.getWaterAdulteration());
                changed = true;
            }
            if (!Objects.equals(entity.getAntibioticResidue(), item.getAntibioticResidue())) {
                entity.setAntibioticResidue(item.getAntibioticResidue());
                changed = true;
            }
            if (!Objects.equals(entity.getBacterialCount(), item.getBacterialCount())) {
                entity.setBacterialCount(item.getBacterialCount());
                changed = true;
            }

            String smellNotes = trimToNull(item.getSmellNotes());
            String rejectionReason = trimToNull(item.getRejectionReason());
            if (item.getQcStatus() == QcStatus.REJECT && rejectionReason == null) {
                throw new IllegalArgumentException("rejectionReason is required when qcStatus is REJECT");
            }
            String labTestAttachmentUrl = trimToNull(item.getLabTestAttachmentUrl());
            if (!Objects.equals(entity.getSmellNotes(), smellNotes)) {
                entity.setSmellNotes(smellNotes);
                changed = true;
            }
            if (!Objects.equals(entity.getRejectionReason(), rejectionReason)) {
                entity.setRejectionReason(rejectionReason);
                changed = true;
            }
            if (!Objects.equals(entity.getLabTestAttachmentUrl(), labTestAttachmentUrl)) {
                entity.setLabTestAttachmentUrl(labTestAttachmentUrl);
                changed = true;
            }

            if (changed) {
                repo.save(entity);
            }
        }

        return list(req.getDate(), req.getShift());
    }

    private void ensureBatchUnlocked(LocalDate date, Shift shift) {
        var batch = milkBatchRepository.findByDateAndShift(date, shift).orElse(null);
        if (batch != null && batch.getQcStatus() == QcStatus.PASS) {
            throw new IllegalArgumentException("Batch locked after QC PASS; milk entries cannot be edited");
        }
    }

    private void validateLiters(Double liters) {
        if (liters == null || Double.isNaN(liters) || Double.isInfinite(liters) || liters < 0) {
            throw new IllegalArgumentException("liters must be a non-negative finite number");
        }
    }

    private void validateNonNegativeMetric(Double value, String fieldName) {
        if (value == null) {
            return;
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            throw new IllegalArgumentException(fieldName + " must be a non-negative finite number");
        }
    }

    private String buildId(LocalDate date, Shift shift, String animalId) {
        return "ME_" + date + "_" + shift + "_" + animalId + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeColorObservation(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private MilkEntryResponse toResponse(MilkEntryEntity e) {
        return MilkEntryResponse.builder()
                .milkEntryId(e.getMilkEntryId())
                .animalId(e.getAnimalId())
                .date(e.getDate())
                .shift(e.getShift())
                .liters(e.getLiters())
                .qcStatus(e.getQcStatus())
                .fat(e.getFat())
                .snf(e.getSnf())
                .temperature(e.getTemperature())
                .lactometer(e.getLactometer())
                .smellNotes(e.getSmellNotes())
                .rejectionReason(e.getRejectionReason())
                .colorObservation(e.getColorObservation())
                .acidity(e.getAcidity())
                .waterAdulteration(e.getWaterAdulteration())
                .antibioticResidue(e.getAntibioticResidue())
                .bacterialCount(e.getBacterialCount())
                .labTestAttachmentUrl(e.getLabTestAttachmentUrl())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
