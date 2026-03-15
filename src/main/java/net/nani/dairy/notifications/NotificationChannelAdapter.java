package net.nani.dairy.notifications;

public interface NotificationChannelAdapter {
    NotificationChannel channel();

    NotificationDispatchResult dispatch(NotificationEventEntity event);
}
