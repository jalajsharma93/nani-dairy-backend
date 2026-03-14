package net.nani.dairy.health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TreatmentTemplateResponse {
    private String templateCode;
    private String title;
    private String diagnosis;
    private String medicineName;
    private String dose;
    private String route;
    private Integer followUpDays;
    private Integer withdrawalDays;
    private boolean prescriptionRequired;
    private String notes;
}
