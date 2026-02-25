package net.nani.dairy.feed.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import net.nani.dairy.feed.FeedSopTaskStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateFeedSopTaskStatusRequest {

    @NotNull
    private FeedSopTaskStatus status;
}
