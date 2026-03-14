package net.nani.dairy.health;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.animals.AnimalRepository;
import net.nani.dairy.health.dto.CreateMedicalTreatmentRequest;
import net.nani.dairy.health.dto.MedicalTreatmentResponse;
import net.nani.dairy.health.dto.TreatmentComplianceSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MedicalTreatmentService {

    private final MedicalTreatmentRepository medicalTreatmentRepository;
    private final AnimalRepository animalRepository;
    private final TreatmentTemplateService treatmentTemplateService;

    public List<MedicalTreatmentResponse> listTreatments(String animalId) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);

        return medicalTreatmentRepository
                .findByAnimalIdOrderByTreatmentDateDescCreatedAtDesc(normalizedAnimalId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public TreatmentComplianceSummaryResponse complianceSummary(LocalDate date, String animalId) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        String scopeAnimalId = trimToNull(animalId);
        List<MedicalTreatmentEntity> rows;
        if (scopeAnimalId != null) {
            validateAnimal(scopeAnimalId);
            rows = medicalTreatmentRepository.findByAnimalIdOrderByTreatmentDateDescCreatedAtDesc(scopeAnimalId);
        } else {
            rows = medicalTreatmentRepository.findAll();
        }

        int compliant = 0;
        int missingPrescription = 0;
        int missingPrescriptionMetadata = 0;
        int missingWithdrawalDate = 0;
        int withdrawalBelowMinimum = 0;
        int activeWithdrawal = 0;
        int followUpDueToday = 0;
        int followUpDueSoon = 0;
        int followUpOverdue = 0;
        LocalDate followUpSoonDate = effectiveDate.plusDays(7);

        for (MedicalTreatmentEntity row : rows) {
            MedicalTreatmentResponse normalized = toResponse(row);
            switch (normalized.getComplianceStatus()) {
                case COMPLIANT -> compliant++;
                case MISSING_PRESCRIPTION -> missingPrescription++;
                case MISSING_PRESCRIPTION_METADATA -> missingPrescriptionMetadata++;
                case MISSING_WITHDRAWAL_DATE -> missingWithdrawalDate++;
                case WITHDRAWAL_BELOW_MINIMUM -> withdrawalBelowMinimum++;
                default -> {
                }
            }

            if (row.getWithdrawalTillDate() != null && !row.getWithdrawalTillDate().isBefore(effectiveDate)) {
                activeWithdrawal++;
            }
            if (row.getFollowUpDate() != null) {
                if (row.getFollowUpDate().isBefore(effectiveDate)) {
                    followUpOverdue++;
                } else if (row.getFollowUpDate().isEqual(effectiveDate)) {
                    followUpDueToday++;
                } else if (!row.getFollowUpDate().isAfter(followUpSoonDate)) {
                    followUpDueSoon++;
                }
            }
        }

        return TreatmentComplianceSummaryResponse.builder()
                .date(effectiveDate)
                .scopeAnimalId(scopeAnimalId)
                .evaluatedRecords(rows.size())
                .compliant(compliant)
                .missingPrescription(missingPrescription)
                .missingPrescriptionMetadata(missingPrescriptionMetadata)
                .missingWithdrawalDate(missingWithdrawalDate)
                .withdrawalBelowMinimum(withdrawalBelowMinimum)
                .activeWithdrawal(activeWithdrawal)
                .followUpDueToday(followUpDueToday)
                .followUpDueSoon(followUpDueSoon)
                .followUpOverdue(followUpOverdue)
                .build();
    }

    public MedicalTreatmentResponse createTreatment(String animalId, CreateMedicalTreatmentRequest req) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);
        NormalizedTreatment normalizedTreatment = normalizeTreatment(req, true);

        MedicalTreatmentEntity entity = MedicalTreatmentEntity.builder()
                .treatmentId(buildTreatmentId())
                .animalId(normalizedAnimalId)
                .treatmentDate(normalizedTreatment.treatmentDate())
                .templateCode(normalizedTreatment.templateCode())
                .diagnosis(normalizedTreatment.diagnosis())
                .medicineName(normalizedTreatment.medicineName())
                .dose(normalizedTreatment.dose())
                .route(normalizedTreatment.route())
                .veterinarianName(normalizedTreatment.veterinarianName())
                .prescriptionPhotoUrl(normalizedTreatment.prescriptionPhotoUrl())
                .prescriptionIssuedBy(normalizedTreatment.prescriptionIssuedBy())
                .prescriptionIssuedDate(normalizedTreatment.prescriptionIssuedDate())
                .prescriptionReferenceNo(normalizedTreatment.prescriptionReferenceNo())
                .withdrawalTillDate(normalizedTreatment.withdrawalTillDate())
                .followUpDate(normalizedTreatment.followUpDate())
                .notes(normalizedTreatment.notes())
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
        NormalizedTreatment normalizedTreatment = normalizeTreatment(req, true);

        MedicalTreatmentEntity entity = medicalTreatmentRepository
                .findByTreatmentIdAndAnimalId(treatmentId, normalizedAnimalId)
                .orElseThrow(() -> notFound("Treatment record not found"));

        entity.setTreatmentDate(normalizedTreatment.treatmentDate());
        entity.setTemplateCode(normalizedTreatment.templateCode());
        entity.setDiagnosis(normalizedTreatment.diagnosis());
        entity.setMedicineName(normalizedTreatment.medicineName());
        entity.setDose(normalizedTreatment.dose());
        entity.setRoute(normalizedTreatment.route());
        entity.setVeterinarianName(normalizedTreatment.veterinarianName());
        entity.setPrescriptionPhotoUrl(normalizedTreatment.prescriptionPhotoUrl());
        entity.setPrescriptionIssuedBy(normalizedTreatment.prescriptionIssuedBy());
        entity.setPrescriptionIssuedDate(normalizedTreatment.prescriptionIssuedDate());
        entity.setPrescriptionReferenceNo(normalizedTreatment.prescriptionReferenceNo());
        entity.setWithdrawalTillDate(normalizedTreatment.withdrawalTillDate());
        entity.setFollowUpDate(normalizedTreatment.followUpDate());
        entity.setNotes(normalizedTreatment.notes());

        return toResponse(medicalTreatmentRepository.save(entity));
    }

    public void deleteTreatment(String animalId, String treatmentId) {
        String normalizedAnimalId = normalizeAnimalId(animalId);
        validateAnimal(normalizedAnimalId);

        MedicalTreatmentEntity entity = medicalTreatmentRepository
                .findByTreatmentIdAndAnimalId(treatmentId, normalizedAnimalId)
                .orElseThrow(() -> notFound("Treatment record not found"));
        medicalTreatmentRepository.delete(entity);
    }

    private MedicalTreatmentResponse toResponse(MedicalTreatmentEntity entity) {
        TreatmentTemplateService.TreatmentRuleProfile profile = treatmentTemplateService.resolveRuleProfile(
                entity.getTemplateCode(),
                entity.getMedicineName()
        );
        TreatmentTemplateService.TreatmentComplianceResult compliance = treatmentTemplateService.evaluateCompliance(
                profile,
                entity.getTreatmentDate(),
                entity.getWithdrawalTillDate(),
                entity.getPrescriptionPhotoUrl(),
                entity.getPrescriptionIssuedBy(),
                entity.getPrescriptionIssuedDate()
        );

        return MedicalTreatmentResponse.builder()
                .treatmentId(entity.getTreatmentId())
                .animalId(entity.getAnimalId())
                .treatmentDate(entity.getTreatmentDate())
                .templateCode(entity.getTemplateCode())
                .templateTitle(profile.templateTitle())
                .diagnosis(entity.getDiagnosis())
                .medicineName(entity.getMedicineName())
                .dose(entity.getDose())
                .route(entity.getRoute())
                .veterinarianName(entity.getVeterinarianName())
                .prescriptionPhotoUrl(entity.getPrescriptionPhotoUrl())
                .prescriptionIssuedBy(entity.getPrescriptionIssuedBy())
                .prescriptionIssuedDate(entity.getPrescriptionIssuedDate())
                .prescriptionReferenceNo(entity.getPrescriptionReferenceNo())
                .withdrawalTillDate(entity.getWithdrawalTillDate())
                .minimumWithdrawalTillDate(compliance.minimumWithdrawalTillDate())
                .prescriptionRequired(profile.prescriptionRequired())
                .complianceStatus(compliance.status())
                .complianceMessage(compliance.message())
                .followUpDate(entity.getFollowUpDate())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private NormalizedTreatment normalizeTreatment(CreateMedicalTreatmentRequest req, boolean enforceCompliance) {
        if (req == null) {
            throw badRequest("Request body is required");
        }

        LocalDate treatmentDate = req.getTreatmentDate();
        if (treatmentDate == null) {
            throw badRequest("treatmentDate is required");
        }

        String normalizedTemplateCode = normalizeTemplateCode(req.getTemplateCode());
        TreatmentTemplateService.TreatmentTemplateDefinition template = normalizedTemplateCode == null
                ? null
                : treatmentTemplateService.findTemplate(normalizedTemplateCode)
                .orElseThrow(() -> badRequest("Unknown treatment templateCode: " + normalizedTemplateCode));

        String diagnosis = firstNonBlank(trimToNull(req.getDiagnosis()), template == null ? null : template.diagnosis());
        if (diagnosis == null) {
            throw badRequest("diagnosis is required");
        }
        String medicineName = firstNonBlank(trimToNull(req.getMedicineName()), template == null ? null : template.medicineName());
        if (medicineName == null) {
            throw badRequest("medicineName is required");
        }

        String dose = firstNonBlank(trimToNull(req.getDose()), template == null ? null : trimToNull(template.dose()));
        String route = firstNonBlank(trimToNull(req.getRoute()), template == null ? null : trimToNull(template.route()));
        LocalDate followUpDate = req.getFollowUpDate();
        if (followUpDate == null && template != null && template.followUpDays() != null && template.followUpDays() > 0) {
            followUpDate = treatmentDate.plusDays(template.followUpDays());
        }
        LocalDate withdrawalTillDate = req.getWithdrawalTillDate();
        if (withdrawalTillDate == null && template != null && template.withdrawalDays() != null && template.withdrawalDays() > 0) {
            withdrawalTillDate = treatmentDate.plusDays(template.withdrawalDays());
        }

        if (withdrawalTillDate != null && withdrawalTillDate.isBefore(treatmentDate)) {
            throw badRequest("withdrawalTillDate cannot be before treatmentDate");
        }
        if (followUpDate != null && followUpDate.isBefore(treatmentDate)) {
            throw badRequest("followUpDate cannot be before treatmentDate");
        }

        String prescriptionPhotoUrl = trimToNull(req.getPrescriptionPhotoUrl());
        String prescriptionIssuedBy = trimToNull(req.getPrescriptionIssuedBy());
        LocalDate prescriptionIssuedDate = req.getPrescriptionIssuedDate();
        String prescriptionReferenceNo = trimToNull(req.getPrescriptionReferenceNo());
        validatePrescriptionUrl(prescriptionPhotoUrl);
        if (prescriptionPhotoUrl != null && (isBlank(prescriptionIssuedBy) || prescriptionIssuedDate == null)) {
            throw badRequest("Prescription issuer and issue date are required when prescriptionPhotoUrl is provided");
        }
        if (prescriptionIssuedDate != null && prescriptionIssuedDate.isAfter(treatmentDate)) {
            throw badRequest("prescriptionIssuedDate cannot be after treatmentDate");
        }

        TreatmentTemplateService.TreatmentRuleProfile profile = treatmentTemplateService.resolveRuleProfile(
                normalizedTemplateCode,
                medicineName
        );
        TreatmentTemplateService.TreatmentComplianceResult compliance = treatmentTemplateService.evaluateCompliance(
                profile,
                treatmentDate,
                withdrawalTillDate,
                prescriptionPhotoUrl,
                prescriptionIssuedBy,
                prescriptionIssuedDate
        );
        if (enforceCompliance && compliance.status() != TreatmentComplianceStatus.COMPLIANT) {
            throw badRequest(compliance.message());
        }

        return new NormalizedTreatment(
                treatmentDate,
                normalizedTemplateCode,
                diagnosis,
                medicineName,
                dose,
                route,
                trimToNull(req.getVeterinarianName()),
                prescriptionPhotoUrl,
                prescriptionIssuedBy,
                prescriptionIssuedDate,
                prescriptionReferenceNo,
                withdrawalTillDate,
                followUpDate,
                trimToNull(req.getNotes())
        );
    }

    private void validateAnimal(String animalId) {
        if (!animalRepository.existsById(animalId)) {
            throw notFound("Animal not found for animalId");
        }
    }

    private String normalizeAnimalId(String animalId) {
        String normalized = animalId == null ? "" : animalId.trim();
        if (normalized.isEmpty()) {
            throw badRequest("animalId is required");
        }
        return normalized;
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first;
        }
        if (!isBlank(second)) {
            return second;
        }
        return null;
    }

    private String normalizeTemplateCode(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void validatePrescriptionUrl(String value) {
        if (value == null) {
            return;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw badRequest("prescriptionPhotoUrl must start with http:// or https://");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String buildTreatmentId() {
        return "TRT_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private record NormalizedTreatment(
            LocalDate treatmentDate,
            String templateCode,
            String diagnosis,
            String medicineName,
            String dose,
            String route,
            String veterinarianName,
            String prescriptionPhotoUrl,
            String prescriptionIssuedBy,
            LocalDate prescriptionIssuedDate,
            String prescriptionReferenceNo,
            LocalDate withdrawalTillDate,
            LocalDate followUpDate,
            String notes
    ) {
    }
}
