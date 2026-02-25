package net.nani.dairy.milk.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import net.nani.dairy.milk.Shift;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateMilkEntryQcRequest {

    @NotNull
    private LocalDate date;

    @NotNull
    private Shift shift;

    @Valid
    @NotEmpty
    private List<MilkEntryQcItemRequest> entries;
}
