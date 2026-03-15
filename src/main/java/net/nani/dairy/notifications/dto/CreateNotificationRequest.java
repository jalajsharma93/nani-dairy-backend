package net.nani.dairy.notifications.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.notifications.NotificationAudienceType;
import net.nani.dairy.notifications.NotificationChannel;
import net.nani.dairy.notifications.NotificationPriority;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateNotificationRequest {

    @NotBlank
    private String eventType;

    @NotBlank
    private String title;

    @NotBlank
    private String message;

    private NotificationPriority priority;
    private NotificationChannel channel;
    private NotificationAudienceType audienceType;

    private String recipientUsername;
    private UserRole recipientRole;
    private List<UserRole> recipientRoles;

    private String sourceRefId;
    private String metadataJson;

    @Min(1)
    @Max(20)
    private Integer maxRetries;
}
