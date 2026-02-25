package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.sales.PaymentStatus;
import net.nani.dairy.sales.ProductType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryChecklistItemResponse {
    private String saleId;
    private LocalDate dispatchDate;
    private String customerName;
    private ProductType productType;
    private double quantity;
    private String routeName;
    private String collectionPoint;
    private boolean delivered;
    private OffsetDateTime deliveredAt;
    private String deliveredBy;
    private String deliveryNote;
    private double totalAmount;
    private double receivedAmount;
    private double pendingAmount;
    private PaymentStatus paymentStatus;
}
