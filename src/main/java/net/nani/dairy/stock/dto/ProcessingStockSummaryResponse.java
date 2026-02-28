package net.nani.dairy.stock.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessingStockSummaryResponse {
    private LocalDate date;

    private int rawMaterialItems;
    private int lowStockRawMaterials;
    private double rawMaterialStockValue;

    private double milkBalanceLiters;
    private double curdBalanceKg;
    private double buttermilkBalanceLiters;
    private double gheeBalanceKg;

    private double milkProducedToday;
    private double milkSoldToday;
    private double curdSoldToday;
    private double buttermilkSoldToday;
    private double gheeSoldToday;
    private double suggestedEodMilkToCurd;

    private int transactionsToday;
}
