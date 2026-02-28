package net.nani.dairy.stock.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncProcessingDayRequest {
    private LocalDate date;
    private Boolean autoTransferMilkToCurd;
}
