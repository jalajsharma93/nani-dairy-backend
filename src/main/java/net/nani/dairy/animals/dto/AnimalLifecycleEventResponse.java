package net.nani.dairy.animals.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.nani.dairy.animals.AnimalStatus;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnimalLifecycleEventResponse {
    private String animalLifecycleEventId;
    private String animalId;
    private AnimalStatus fromStatus;
    private AnimalStatus toStatus;
    private Boolean fromActive;
    private boolean toActive;
    private String reason;
    private String changedBy;
    private OffsetDateTime changedAt;
}
