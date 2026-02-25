package net.nani.dairy.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import net.nani.dairy.feed.FeedRationPhase;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateFeedRecipeRequest {

    @NotBlank
    private String recipeName;

    @NotNull
    private FeedRationPhase rationPhase;

    @PositiveOrZero
    private Integer targetAnimalCount;

    @NotBlank
    private String ingredients;

    private String instructions;

    @NotNull
    private Boolean active;
}
