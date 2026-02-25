package net.nani.dairy.feed.dto;

import lombok.*;
import net.nani.dairy.feed.FeedRationPhase;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedRecipeResponse {
    private String feedRecipeId;
    private String recipeName;
    private FeedRationPhase rationPhase;
    private Integer targetAnimalCount;
    private String ingredients;
    private String instructions;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
