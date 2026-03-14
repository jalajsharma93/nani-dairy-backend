package net.nani.dairy.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.sales.CustomerType;
import net.nani.dairy.sales.SubscriptionFrequency;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCustomerRecordRequest {

    @NotBlank
    @Size(max = 120)
    private String customerName;

    @NotNull
    private CustomerType customerType;

    @Size(max = 40)
    private String phone;

    @Size(max = 80)
    private String routeName;

    @Size(max = 120)
    private String collectionPoint;

    @NotNull
    private Boolean subscriptionActive;

    @Positive
    private Double dailySubscriptionQty;

    private SubscriptionFrequency subscriptionFrequency;

    private LocalDate subscriptionPausedUntil;

    @Size(max = 400)
    private String subscriptionSkipDatesCsv;

    @Size(max = 140)
    private String subscriptionHolidayWeekdaysCsv;

    @Positive
    private Double defaultMilkUnitPrice;

    @NotNull
    private Boolean isActive;

    @Size(max = 500)
    private String notes;
}
