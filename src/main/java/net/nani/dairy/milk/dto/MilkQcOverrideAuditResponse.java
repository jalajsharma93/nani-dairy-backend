package net.nani.dairy.milk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.milk.QcStatus;
import net.nani.dairy.milk.Shift;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MilkQcOverrideAuditResponse {

    private String milkQcOverrideAuditId;
    private LocalDate batchDate;
    private Shift shift;
    private QcStatus requestedQcStatus;
    private QcStatus recommendedQcStatus;
    private QcStatus appliedQcStatus;
    private boolean overrideRequested;
    private boolean overrideApproved;
    private String overrideReason;
    private String triggerCodesCsv;
    private String actorUsername;
    private String actorRole;
    private OffsetDateTime createdAt;
}
