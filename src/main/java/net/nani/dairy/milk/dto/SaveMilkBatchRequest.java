package net.nani.dairy.milk.dto;

import net.nani.dairy.milk.Shift;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveMilkBatchRequest {

    @NotNull
    private LocalDate date;

    @NotNull
    private Shift shift;

    @NotNull
    @PositiveOrZero
    private Double totalLiters;
}