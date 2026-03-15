package net.nani.dairy.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.notifications.NotificationChannel;
import net.nani.dairy.notifications.NotificationDeliveryStatus;
import net.nani.dairy.notifications.NotificationPriority;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private String notificationId;
    private String eventType;
    private String title;
    private String message;
    private NotificationPriority priority;
    private NotificationChannel channel;
    private NotificationDeliveryStatus deliveryStatus;
    private String recipientUsername;
    private UserRole recipientRole;
    private String sourceRefId;
    private String metadataJson;
    private OffsetDateTime readAt;
    private OffsetDateTime deliveredAt;
    private Integer retryCount;
    private Integer maxRetries;
    private String lastError;
    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private boolean read;
}
