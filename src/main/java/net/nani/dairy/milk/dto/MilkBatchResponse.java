package net.nani.dairy.milk.dto;


import net.nani.dairy.milk.QcStatus;
import net.nani.dairy.milk.Shift;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class MilkBatchResponse {
    private String milkBatchId;
    private LocalDate date;
    private Shift shift;
    private double totalLiters;
    private QcStatus qcStatus;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}