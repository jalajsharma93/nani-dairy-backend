package net.nani.dairy.sales.dto;

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
public class CustomerSubscriptionInvoiceLineItemResponse {

    private String code;
    private String label;
    private double quantity;
    private double unitPrice;
    private double amount;
    private String note;
}
