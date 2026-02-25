package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.sales.CustomerType;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerRecordResponse {
    private String customerId;
    private String customerName;
    private CustomerType customerType;
    private String phone;
    private String routeName;
    private String collectionPoint;
    private boolean subscriptionActive;
    private Double dailySubscriptionQty;
    private boolean isActive;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
