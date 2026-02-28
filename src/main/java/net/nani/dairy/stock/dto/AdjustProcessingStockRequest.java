package net.nani.dairy.stock.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import net.nani.dairy.stock.ProcessingStockStage;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjustProcessingStockRequest {

    private LocalDate date;

    @NotNull
    private ProcessingStockStage stage;

    @NotNull
    private Double quantityDelta;

    private String notes;
}
