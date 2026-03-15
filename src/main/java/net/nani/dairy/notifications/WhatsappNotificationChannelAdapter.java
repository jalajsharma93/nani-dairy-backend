package net.nani.dairy.notifications;

import org.springframework.stereotype.Component;

@Component
public class WhatsappNotificationChannelAdapter implements NotificationChannelAdapter {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public NotificationDispatchResult dispatch(NotificationEventEntity event) {
        return NotificationDispatchResult.failed("WhatsApp adapter is not configured yet");
    }
}
