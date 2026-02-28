package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthCloseSettlementBulkResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;
    private int requestedCount;
    private int succeededCount;
    private int failedCount;
    private String processedBy;
    private OffsetDateTime processedAt;
    private String note;
    private List<MonthCloseSettlementBulkItemResponse> results;
}
