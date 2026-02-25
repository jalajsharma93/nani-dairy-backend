package net.nani.dairy.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDewormingRequest {

    @NotBlank
    private String drugName;

    @NotNull
    private LocalDate doseDate;

    private LocalDate nextDueDate;

    private Double weightAtDoseKg;

    private String notes;
}
