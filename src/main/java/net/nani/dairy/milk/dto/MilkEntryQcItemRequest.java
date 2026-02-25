package net.nani.dairy.milk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import net.nani.dairy.milk.QcStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MilkEntryQcItemRequest {

    @NotBlank
    private String animalId;

    @NotNull
    private QcStatus qcStatus;

    private Double fat;
    private Double snf;
    private Double temperature;
    private Double lactometer;
    private String smellNotes;
    private String rejectionReason;
}
