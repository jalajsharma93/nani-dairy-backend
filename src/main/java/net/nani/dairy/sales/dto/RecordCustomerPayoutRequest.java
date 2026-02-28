package net.nani.dairy.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
public class RecordCustomerPayoutRequest {

    @NotNull
    @Positive
    private Double amount;

    private LocalDate payoutDate;

    @Size(max = 300)
    private String note;
}
