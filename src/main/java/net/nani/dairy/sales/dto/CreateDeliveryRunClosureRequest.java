package net.nani.dairy.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.milk.Shift;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDeliveryRunClosureRequest {

    @NotNull
    private LocalDate date;

    @NotBlank
    @Size(max = 120)
    private String routeName;

    @NotNull
    private Shift shift;

    @Min(0)
    private Long totalStops;

    @Min(0)
    private Long deliveredStops;

    @Min(0)
    private Long pendingStops;

    @Min(0)
    private Long skippedStops;

    @PositiveOrZero
    private Double expectedCollection;

    @PositiveOrZero
    private Double actualCollection;

    @PositiveOrZero
    private Double cashCollection;

    @PositiveOrZero
    private Double upiCollection;

    @PositiveOrZero
    private Double otherCollection;

    @Size(max = 500)
    private String notes;
}
