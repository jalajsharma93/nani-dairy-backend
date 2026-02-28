package net.nani.dairy.sales.dto;

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
public class UpdateDeliveryTaskAssigneeRequest {

    @Size(max = 100)
    private String assignedToUsername;

    @Size(max = 500)
    private String notes;
}
