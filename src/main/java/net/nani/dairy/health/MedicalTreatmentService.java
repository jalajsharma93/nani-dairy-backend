package net.nani.dairy.health;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.animals.AnimalRepository;
import net.nani.dairy.health.dto.CreateMedicalTreatmentRequest;
import net.nani.dairy.health.dto.MedicalTreatmentResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MedicalTreatmentService {

    private final MedicalTreatmentRepository medicalTreatmentRepository;
    private final AnimalRepository animalRepository;

    public List<MedicalTreatmentResponse> listTreatments(String animalId) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);

        return medicalTreatmentRepository
                .findByAnimalIdOrderByTreatmentDateDescCreatedAtDesc(normalizedAnimalId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MedicalTreatmentResponse createTreatment(String animalId, CreateMedicalTreatmentRequest req) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);
        validateTreatmentDates(req);

        MedicalTreatmentEntity entity = MedicalTreatmentEntity.builder()
                .treatmentId(buildTreatmentId())
                .animalId(normalizedAnimalId)
                .treatmentDate(req.getTreatmentDate())
                .diagnosis(trimRequired(req.getDiagnosis(), "diagnosis"))
                .medicineName(trimRequired(req.getMedicineName(), "medicineName"))
                .dose(trimToNull(req.getDose()))
                .route(trimToNull(req.getRoute()))
                .veterinarianName(trimToNull(req.getVeterinarianName()))
                .prescriptionPhotoUrl(trimToNull(req.getPrescriptionPhotoUrl()))
                .withdrawalTillDate(req.getWithdrawalTillDate())
                .followUpDate(req.getFollowUpDate())
                .notes(trimToNull(req.getNotes()))
                .build();

        return toResponse(medicalTreatmentRepository.save(entity));
    }

    public MedicalTreatmentResponse updateTreatment(
            String animalId,
            String treatmentId,
            CreateMedicalTreatmentRequest req
    ) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);
        validateTreatmentDates(req);

        MedicalTreatmentEntity entity = medicalTreatmentRepository
                .findByTreatmentIdAndAnimalId(treatmentId, normalizedAnimalId)
                .orElseThrow(() -> new IllegalArgumentException("Treatment record not found"));

        entity.setTreatmentDate(req.getTreatmentDate());
        entity.setDiagnosis(trimRequired(req.getDiagnosis(), "diagnosis"));
        entity.setMedicineName(trimRequired(req.getMedicineName(), "medicineName"));
        entity.setDose(trimToNull(req.getDose()));
        entity.setRoute(trimToNull(req.getRoute()));
        entity.setVeterinarianName(trimToNull(req.getVeterinarianName()));
        entity.setPrescriptionPhotoUrl(trimToNull(req.getPrescriptionPhotoUrl()));
        entity.setWithdrawalTillDate(req.getWithdrawalTillDate());
        entity.setFollowUpDate(req.getFollowUpDate());
        entity.setNotes(trimToNull(req.getNotes()));

        return toResponse(medicalTreatmentRepository.save(entity));
    }

    public void deleteTreatment(String animalId, String treatmentId) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);

        MedicalTreatmentEntity entity = medicalTreatmentRepository
                .findByTreatmentIdAndAnimalId(treatmentId, normalizedAnimalId)
                .orElseThrow(() -> new IllegalArgumentException("Treatment record not found"));
        medicalTreatmentRepository.delete(entity);
    }

    private MedicalTreatmentResponse toResponse(MedicalTreatmentEntity entity) {
        return MedicalTreatmentResponse.builder()
                .treatmentId(entity.getTreatmentId())
                .animalId(entity.getAnimalId())
                .treatmentDate(entity.getTreatmentDate())
                .diagnosis(entity.getDiagnosis())
                .medicineName(entity.getMedicineName())
                .dose(entity.getDose())
                .route(entity.getRoute())
                .veterinarianName(entity.getVeterinarianName())
                .prescriptionPhotoUrl(entity.getPrescriptionPhotoUrl())
                .withdrawalTillDate(entity.getWithdrawalTillDate())
                .followUpDate(entity.getFollowUpDate())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void validateTreatmentDates(CreateMedicalTreatmentRequest req) {
        LocalDate treatmentDate = req.getTreatmentDate();
        if (treatmentDate == null) {
            throw new IllegalArgumentException("treatmentDate is required");
        }
        if (req.getWithdrawalTillDate() != null && req.getWithdrawalTillDate().isBefore(treatmentDate)) {
            throw new IllegalArgumentException("withdrawalTillDate cannot be before treatmentDate");
        }
        if (req.getFollowUpDate() != null && req.getFollowUpDate().isBefore(treatmentDate)) {
            throw new IllegalArgumentException("followUpDate cannot be before treatmentDate");
        }
    }

    private void validateAnimal(String animalId) {
        if (!animalRepository.existsById(animalId)) {
            throw new IllegalArgumentException("Animal not found for animalId");
        }
    }

    private String normalizeAnimalId(String animalId) {
        String normalized = animalId == null ? "" : animalId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("animalId is required");
        }
        return normalized;
    }

    private String trimRequired(String value, String fieldName) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildTreatmentId() {
        return "TRT_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
