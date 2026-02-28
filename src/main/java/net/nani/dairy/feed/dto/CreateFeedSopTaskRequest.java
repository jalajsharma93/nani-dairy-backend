package net.nani.dairy.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.feed.FeedSopTaskPriority;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFeedSopTaskRequest {

    @NotNull
    private LocalDate taskDate;

    @NotBlank
    private String title;

    private String details;
    private UserRole assignedRole;
    private String assignedToUsername;
    private FeedSopTaskPriority priority;
    private LocalTime dueTime;
}
