package net.nani.dairy.health;

import net.nani.dairy.health.dto.TreatmentTemplateResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TreatmentTemplateService {

    private static final Set<String> ANTIBIOTIC_KEYWORDS = Set.of(
            "cef",
            "amoxi",
            "penicillin",
            "enro",
            "cipro",
            "oxy",
            "sulfa",
            "gentamicin",
            "tetracycline"
    );

    private static final Map<String, Integer> MEDICINE_WITHDRAWAL_DAYS = Map.of(
            "ceftriaxone", 4,
            "oxytetracycline", 5,
            "enrofloxacin", 5,
            "amoxicillin", 4,
            "penicillin", 4,
            "meloxicam", 2
    );

    private static final List<TreatmentTemplateDefinition> TEMPLATES = List.of(
            new TreatmentTemplateDefinition(
                    "MASTITIS_ABX",
                    "Mastitis Antibiotic Course",
                    "Mastitis",
                    "Ceftriaxone",
                    "10 ml",
                    "IM",
                    3,
                    4,
                    true,
                    "Use sterile protocol and post-milking teat dip."
            ),
            new TreatmentTemplateDefinition(
                    "FEVER_SUPPORT",
                    "Fever Supportive Care",
                    "Fever",
                    "Meloxicam",
                    "8 ml",
                    "IM",
                    2,
                    2,
                    true,
                    "Monitor appetite and hydration for 48 hours."
            ),
            new TreatmentTemplateDefinition(
                    "RESPIRATORY_ABX",
                    "Respiratory Infection Protocol",
                    "Respiratory infection",
                    "Oxytetracycline",
                    "10 ml",
                    "IM",
                    3,
                    5,
                    true,
                    "Recheck breathing and temperature daily."
            ),
            new TreatmentTemplateDefinition(
                    "WOUND_CARE",
                    "Wound Care",
                    "Wound/Injury",
                    "Povidone Iodine Spray",
                    "Topical as needed",
                    "Topical",
                    5,
                    0,
                    false,
                    "Clean wound area before every dressing."
            ),
            new TreatmentTemplateDefinition(
                    "CALCIUM_SUPPORT",
                    "Calcium Support",
                    "Milk fever/hypocalcemia",
                    "Calcium borogluconate",
                    "250 ml",
                    "IV",
                    2,
                    1,
                    true,
                    "Observe standing response and repeat only on vet advice."
            )
    );

    private final Map<String, TreatmentTemplateDefinition> templatesByCode = TEMPLATES
            .stream()
            .collect(Collectors.toUnmodifiableMap(
                    template -> template.templateCode().toUpperCase(Locale.ROOT),
                    template -> template
            ));

    public List<TreatmentTemplateResponse> listTemplates() {
        return TEMPLATES.stream()
                .map(template -> TreatmentTemplateResponse.builder()
                        .templateCode(template.templateCode())
                        .title(template.title())
                        .diagnosis(template.diagnosis())
                        .medicineName(template.medicineName())
                        .dose(template.dose())
                        .route(template.route())
                        .followUpDays(template.followUpDays())
                        .withdrawalDays(template.withdrawalDays())
                        .prescriptionRequired(template.prescriptionRequired())
                        .notes(template.notes())
                        .build())
                .toList();
    }

    public Optional<TreatmentTemplateDefinition> findTemplate(String templateCode) {
        String normalizedCode = normalizeCode(templateCode);
        if (normalizedCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(templatesByCode.get(normalizedCode));
    }

    public TreatmentRuleProfile resolveRuleProfile(String templateCode, String medicineName) {
        Optional<TreatmentTemplateDefinition> template = findTemplate(templateCode);
        if (template.isPresent()) {
            TreatmentTemplateDefinition row = template.get();
            return new TreatmentRuleProfile(
                    row.templateCode(),
                    row.title(),
                    row.followUpDays(),
                    toPositiveOrNull(row.withdrawalDays()),
                    row.prescriptionRequired()
            );
        }

        String normalizedMedicine = normalizeMedicineName(medicineName);
        Integer minimumWithdrawalDays = null;
        for (Map.Entry<String, Integer> entry : MEDICINE_WITHDRAWAL_DAYS.entrySet()) {
            if (normalizedMedicine.contains(entry.getKey())) {
                minimumWithdrawalDays = entry.getValue();
                break;
            }
        }

        boolean prescriptionRequired = appearsAntibiotic(normalizedMedicine)
                || minimumWithdrawalDays != null;

        return new TreatmentRuleProfile(
                null,
                null,
                null,
                minimumWithdrawalDays,
                prescriptionRequired
        );
    }

    public TreatmentComplianceResult evaluateCompliance(
            TreatmentRuleProfile profile,
            LocalDate treatmentDate,
            LocalDate withdrawalTillDate,
            String prescriptionPhotoUrl,
            String prescriptionIssuedBy,
            LocalDate prescriptionIssuedDate
    ) {
        boolean missingPrescription = profile.prescriptionRequired()
                && isBlank(prescriptionPhotoUrl);
        if (missingPrescription) {
            return new TreatmentComplianceResult(
                    TreatmentComplianceStatus.MISSING_PRESCRIPTION,
                    "Prescription evidence is required for this treatment.",
                    null
            );
        }

        boolean missingPrescriptionMetadata = profile.prescriptionRequired()
                && (isBlank(prescriptionIssuedBy) || prescriptionIssuedDate == null);
        if (missingPrescriptionMetadata) {
            return new TreatmentComplianceResult(
                    TreatmentComplianceStatus.MISSING_PRESCRIPTION_METADATA,
                    "Prescription issuer and issue date are required for this treatment.",
                    null
            );
        }

        Integer minimumWithdrawalDays = profile.minimumWithdrawalDays();
        if (minimumWithdrawalDays != null && minimumWithdrawalDays > 0) {
            LocalDate minimumWithdrawalTillDate = treatmentDate.plusDays(minimumWithdrawalDays);
            if (withdrawalTillDate == null) {
                return new TreatmentComplianceResult(
                        TreatmentComplianceStatus.MISSING_WITHDRAWAL_DATE,
                        "Withdrawal end date is required for this treatment.",
                        minimumWithdrawalTillDate
                );
            }
            if (withdrawalTillDate.isBefore(minimumWithdrawalTillDate)) {
                return new TreatmentComplianceResult(
                        TreatmentComplianceStatus.WITHDRAWAL_BELOW_MINIMUM,
                        "Withdrawal end date must be on or after " + minimumWithdrawalTillDate + ".",
                        minimumWithdrawalTillDate
                );
            }
            return new TreatmentComplianceResult(
                    TreatmentComplianceStatus.COMPLIANT,
                    "Compliant",
                    minimumWithdrawalTillDate
            );
        }

        return new TreatmentComplianceResult(
                TreatmentComplianceStatus.COMPLIANT,
                "Compliant",
                null
        );
    }

    private boolean appearsAntibiotic(String normalizedMedicineName) {
        if (normalizedMedicineName.isBlank()) {
            return false;
        }
        return ANTIBIOTIC_KEYWORDS.stream().anyMatch(normalizedMedicineName::contains);
    }

    private String normalizeCode(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeMedicineName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Integer toPositiveOrNull(Integer value) {
        if (value == null || value <= 0) {
            return null;
        }
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record TreatmentTemplateDefinition(
            String templateCode,
            String title,
            String diagnosis,
            String medicineName,
            String dose,
            String route,
            Integer followUpDays,
            Integer withdrawalDays,
            boolean prescriptionRequired,
            String notes
    ) {
    }

    public record TreatmentRuleProfile(
            String templateCode,
            String templateTitle,
            Integer followUpDays,
            Integer minimumWithdrawalDays,
            boolean prescriptionRequired
    ) {
    }

    public record TreatmentComplianceResult(
            TreatmentComplianceStatus status,
            String message,
            LocalDate minimumWithdrawalTillDate
    ) {
    }
}
