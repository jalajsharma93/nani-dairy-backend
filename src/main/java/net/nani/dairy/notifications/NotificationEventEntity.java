package net.nani.dairy.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.auth.UserRole;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "notification_event",
        indexes = {
                @Index(name = "idx_notification_recipient", columnList = "recipient_username"),
                @Index(name = "idx_notification_status", columnList = "delivery_status"),
                @Index(name = "idx_notification_priority", columnList = "priority"),
                @Index(name = "idx_notification_created", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEventEntity {

    @Id
    @Column(name = "notification_id", length = 80, nullable = false)
    private String notificationId;

    @Column(name = "event_type", length = 80, nullable = false)
    private String eventType;

    @Column(name = "title", length = 160, nullable = false)
    private String title;

    @Column(name = "message", length = 1200, nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20, nullable = false)
    private NotificationPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 20, nullable = false)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 20, nullable = false)
    private NotificationDeliveryStatus deliveryStatus;

    @Column(name = "recipient_username", length = 100, nullable = false)
    private String recipientUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_role", length = 30, nullable = false)
    private UserRole recipientRole;

    @Column(name = "source_ref_id", length = 160)
    private String sourceRefId;

    @Column(name = "metadata_json", length = 2000)
    private String metadataJson;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (priority == null) {
            priority = NotificationPriority.MEDIUM;
        }
        if (channel == null) {
            channel = NotificationChannel.IN_APP;
        }
        if (deliveryStatus == null) {
            deliveryStatus = NotificationDeliveryStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetries == null) {
            maxRetries = 5;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}

