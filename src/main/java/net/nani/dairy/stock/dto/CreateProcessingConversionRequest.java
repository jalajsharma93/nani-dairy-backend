package net.nani.dairy.stock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import net.nani.dairy.stock.ProcessingStockStage;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProcessingConversionRequest {

    private LocalDate date;

    @NotNull
    private ProcessingStockStage fromStage;

    @NotNull
    private ProcessingStockStage toStage;

    @NotNull
    @Positive
    private Double inputQty;

    @NotNull
    @Positive
    private Double outputQty;

    private String notes;
}
