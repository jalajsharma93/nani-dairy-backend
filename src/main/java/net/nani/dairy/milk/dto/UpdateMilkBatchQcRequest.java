package net.nani.dairy.milk.dto;


import net.nani.dairy.milk.QcStatus;
import net.nani.dairy.milk.Shift;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @Getter
    private Boolean overrideRecommendedStatus;

    @Size(max = 700)
    @Getter
    private String overrideReason;

    @Size(max = 80)
    @Getter
    private String approvalRequestId;

    public LocalDate getDate() {
        return date != null ? date : LocalDate.now();
    }

}
