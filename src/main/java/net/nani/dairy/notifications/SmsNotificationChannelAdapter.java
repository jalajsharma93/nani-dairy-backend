package net.nani.dairy.notifications;

import org.springframework.stereotype.Component;

@Component
public class SmsNotificationChannelAdapter implements NotificationChannelAdapter {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public NotificationDispatchResult dispatch(NotificationEventEntity event) {
        return NotificationDispatchResult.failed("SMS adapter is not configured yet");
    }
}
