package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.milk.Shift;
import net.nani.dairy.sales.ProductType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSubscriptionLineResponse {
    private String subscriptionLineId;
    private String customerId;
    private Shift taskShift;
    private ProductType productType;
    private double quantity;
    private double unitPrice;
    private LocalTime preferredTime;
    private String activeDaysCsv;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean active;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
