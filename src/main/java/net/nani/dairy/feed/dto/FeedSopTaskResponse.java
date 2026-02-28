package net.nani.dairy.feed.dto;

import lombok.*;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.feed.FeedSopTaskPriority;
import net.nani.dairy.feed.FeedSopTaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedSopTaskResponse {
    private String feedTaskId;
    private LocalDate taskDate;
    private String title;
    private String details;
    private UserRole assignedRole;
    private String assignedToUsername;
    private String assignedByUsername;
    private LocalDateTime assignedAt;
    private FeedSopTaskPriority priority;
    private FeedSopTaskStatus status;
    private LocalTime dueTime;
    private LocalDateTime completedAt;
    private String completedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
