package net.nani.dairy.integration;

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

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "integration_event",
        indexes = {
                @Index(name = "idx_integration_event_connector_id", columnList = "connector_id"),
                @Index(name = "idx_integration_event_status", columnList = "status"),
                @Index(name = "idx_integration_event_received", columnList = "received_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationEventEntity {

    @Id
    @Column(name = "integration_event_id", length = 80, nullable = false)
    private String integrationEventId;

    @Column(name = "connector_id", length = 80, nullable = false)
    private String connectorId;

    @Column(name = "connector_key", length = 120, nullable = false)
    private String connectorKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", length = 40, nullable = false)
    private ConnectorType connectorType;

    @Column(name = "external_event_id", length = 160)
    private String externalEventId;

    @Column(name = "device_id", length = 160)
    private String deviceId;

    @Column(name = "event_type", length = 120)
    private String eventType;

    @Column(name = "source_ip", length = 100)
    private String sourceIp;

    @Column(name = "raw_payload_json", length = 8000)
    private String rawPayloadJson;

    @Column(name = "normalized_payload_json", length = 8000)
    private String normalizedPayloadJson;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private IntegrationIngestStatus status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (receivedAt == null) {
            receivedAt = now;
        }
        if (status == null) {
            status = IntegrationIngestStatus.RECEIVED;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
