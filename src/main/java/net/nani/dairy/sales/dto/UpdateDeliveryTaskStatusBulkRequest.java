package net.nani.dairy.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class UpdateDeliveryTaskStatusBulkRequest {

    @NotEmpty
    @Size(max = 500)
    private List<@Valid UpdateDeliveryTaskStatusBulkItemRequest> items;
}
