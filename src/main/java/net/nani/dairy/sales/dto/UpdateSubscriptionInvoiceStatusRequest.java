package net.nani.dairy.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSubscriptionInvoiceStatusRequest {

    @NotBlank
    private String customerId;

    @NotBlank
    private String month;

    @Size(max = 700)
    private String note;

    @Size(max = 700)
    private String overrideReason;
}
