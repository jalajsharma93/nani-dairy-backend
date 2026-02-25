package net.nani.dairy.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.feed.FeedSopTaskPriority;
import net.nani.dairy.feed.FeedSopTaskStatus;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateFeedSopTaskRequest {

    @NotNull
    private LocalDate taskDate;

    @NotBlank
    private String title;

    private String details;

    @NotNull
    private UserRole assignedRole;

    @NotNull
    private FeedSopTaskPriority priority;

    @NotNull
    private FeedSopTaskStatus status;

    private LocalTime dueTime;
}
