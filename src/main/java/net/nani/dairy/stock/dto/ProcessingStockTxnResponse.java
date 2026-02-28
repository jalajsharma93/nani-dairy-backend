package net.nani.dairy.stock.dto;

import lombok.*;
import net.nani.dairy.stock.ProcessingStockStage;
import net.nani.dairy.stock.ProcessingStockTxnType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessingStockTxnResponse {
    private String stockTxnId;
    private LocalDate txnDate;
    private ProcessingStockTxnType txnType;
    private String sourceKey;
    private ProcessingStockStage fromStage;
    private Double inputQty;
    private ProcessingStockStage toStage;
    private Double outputQty;
    private String notes;
    private String actorUsername;
    private OffsetDateTime createdAt;
}
