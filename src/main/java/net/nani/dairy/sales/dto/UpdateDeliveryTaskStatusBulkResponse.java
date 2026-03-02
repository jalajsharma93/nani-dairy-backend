package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDeliveryTaskStatusBulkResponse {

    private int totalCount;
    private int successCount;
    private int failedCount;
    private List<UpdateDeliveryTaskStatusBulkItemResponse> items;
}
