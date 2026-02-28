package net.nani.dairy.employees.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkUpsertEmployeeAttendanceRequest {

    @NotEmpty
    @Valid
    private List<UpsertEmployeeAttendanceRequest> entries;
}
