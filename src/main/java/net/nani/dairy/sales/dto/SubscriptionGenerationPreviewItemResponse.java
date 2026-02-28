package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.milk.Shift;
import net.nani.dairy.sales.ProductType;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionGenerationPreviewItemResponse {
    private String source;
    private LocalDate date;
    private String customerId;
    private String customerName;
    private String routeName;
    private String subscriptionLineId;
    private Shift shift;
    private ProductType productType;
    private double quantity;
    private double unitPrice;
    private String activeDaysCsv;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean eligible;
    private String reason;
}
