package net.nani.dairy.feed.dto;

import lombok.*;
import net.nani.dairy.feed.FeedRationPhase;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedLogResponse {
    private String feedLogId;
    private LocalDate feedDate;
    private String animalId;
    private String feedType;
    private FeedRationPhase rationPhase;
    private double quantityKg;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
