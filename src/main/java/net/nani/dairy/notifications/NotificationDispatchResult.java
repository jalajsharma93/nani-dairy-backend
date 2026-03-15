package net.nani.dairy.notifications;

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
public class NotificationDispatchResult {
    private boolean success;
    private String errorMessage;
    private OffsetDateTime deliveredAt;

    public static NotificationDispatchResult sent() {
        return NotificationDispatchResult.builder()
                .success(true)
                .deliveredAt(OffsetDateTime.now())
                .build();
    }

    public static NotificationDispatchResult failed(String errorMessage) {
        return NotificationDispatchResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
