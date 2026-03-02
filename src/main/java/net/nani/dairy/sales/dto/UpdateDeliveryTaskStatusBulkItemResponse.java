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
public class UpdateDeliveryTaskStatusBulkItemResponse {

    private String deliveryTaskId;
    private boolean success;
    private String errorMessage;
    private DeliveryTaskResponse task;
}
