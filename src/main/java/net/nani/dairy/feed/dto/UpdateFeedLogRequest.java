package net.nani.dairy.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import net.nani.dairy.feed.FeedRationPhase;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateFeedLogRequest {

    @NotNull
    private LocalDate feedDate;

    @NotBlank
    private String animalId;

    @NotBlank
    private String feedType;

    private FeedRationPhase rationPhase;

    @NotNull
    @Positive
    private Double quantityKg;

    private String notes;

    private OffsetDateTime expectedUpdatedAt;
}
