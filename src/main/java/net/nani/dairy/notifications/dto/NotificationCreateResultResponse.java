package net.nani.dairy.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.notifications.NotificationAudienceType;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationCreateResultResponse {
    private NotificationAudienceType audienceType;
    private int recipientCount;
    private int createdCount;
    private int sentCount;
    private int failedCount;
    private List<NotificationResponse> notifications;
}
