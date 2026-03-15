package net.nani.dairy.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.auth.UserRole;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationUnreadCountResponse {
    private boolean allRecipients;
    private String recipientUsername;
    private UserRole recipientRole;
    private long unreadCount;
}
