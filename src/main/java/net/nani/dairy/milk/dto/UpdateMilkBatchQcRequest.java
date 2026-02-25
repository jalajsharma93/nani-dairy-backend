package net.nani.dairy.milk.dto;


import net.nani.dairy.milk.QcStatus;
import net.nani.dairy.milk.Shift;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;

@Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateMilkBatchQcRequest {

    @JsonProperty("date")
    private LocalDate date;

    @NotNull
    @Getter
    private Shift shift;

    @NotNull
    @Getter
    private QcStatus qcStatus;

    public LocalDate getDate() {
        return date != null ? date : LocalDate.now();
    }

}