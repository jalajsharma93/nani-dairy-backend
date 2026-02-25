package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.milk.Shift;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleComplianceOverrideAuditResponse {
    private String saleOverrideAuditId;
    private String saleId;
    private String actionType;
    private LocalDate dispatchDate;
    private LocalDate batchDate;
    private Shift batchShift;
    private String customerName;
    private String actorUsername;
    private String overrideReason;
    private String blockedAnimalIds;
    private String blockedAnimalTags;
    private OffsetDateTime createdAt;
}
