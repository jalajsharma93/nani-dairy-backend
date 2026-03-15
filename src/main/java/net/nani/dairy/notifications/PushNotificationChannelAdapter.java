package net.nani.dairy.notifications;

import org.springframework.stereotype.Component;

@Component
public class PushNotificationChannelAdapter implements NotificationChannelAdapter {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public NotificationDispatchResult dispatch(NotificationEventEntity event) {
        return NotificationDispatchResult.failed("Push adapter is not configured yet");
    }
}
