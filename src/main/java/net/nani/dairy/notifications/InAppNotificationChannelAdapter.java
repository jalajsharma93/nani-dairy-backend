package net.nani.dairy.notifications;

import org.springframework.stereotype.Component;

@Component
public class InAppNotificationChannelAdapter implements NotificationChannelAdapter {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public NotificationDispatchResult dispatch(NotificationEventEntity event) {
        return NotificationDispatchResult.sent();
    }
}
