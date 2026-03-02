package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSubscriptionStatementDailyRowResponse {

    private LocalDate date;
    private String dayOfWeek;
    private String status;
    private double expectedQty;
    private double expectedAmount;
}
