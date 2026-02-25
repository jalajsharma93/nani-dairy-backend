package net.nani.dairy.milk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MilkEntryItemRequest {

    @NotBlank
    private String animalId;

    @NotNull
    @PositiveOrZero
    private Double liters;
}
