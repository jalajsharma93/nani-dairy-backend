package net.nani.dairy.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationMarkAllReadResponse {
    private boolean allRecipients;
    private String recipientUsername;
    private int markedCount;
    private OffsetDateTime readAt;
}
