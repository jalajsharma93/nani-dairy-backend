package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.sales.CustomerType;
import net.nani.dairy.sales.SubscriptionFrequency;

import java.time.LocalDate;
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
    private SubscriptionFrequency subscriptionFrequency;
    private LocalDate subscriptionPausedUntil;
    private String subscriptionSkipDatesCsv;
    private String subscriptionHolidayWeekdaysCsv;
    private double runningBalance;
    private double totalPaid;
    private LocalDate lastPayoutDate;
    private Double defaultMilkUnitPrice;
    private boolean isActive;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
