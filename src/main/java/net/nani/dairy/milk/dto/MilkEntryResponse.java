package net.nani.dairy.milk.dto;

import lombok.*;
import net.nani.dairy.milk.QcStatus;
import net.nani.dairy.milk.Shift;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MilkEntryResponse {
    private String milkEntryId;
    private String animalId;
    private LocalDate date;
    private Shift shift;
    private double liters;
    private QcStatus qcStatus;
    private Double fat;
    private Double snf;
    private Double temperature;
    private Double lactometer;
    private String smellNotes;
    private String rejectionReason;
    private String colorObservation;
    private Double acidity;
    private Boolean waterAdulteration;
    private Boolean antibioticResidue;
    private Double bacterialCount;
    private String labTestAttachmentUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
