package net.nani.dairy.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.integration.ConnectorType;
import net.nani.dairy.integration.IntegrationIngestStatus;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationEventResponse {
    private String integrationEventId;
    private String connectorId;
    private String connectorKey;
    private ConnectorType connectorType;
    private String externalEventId;
    private String deviceId;
    private String eventType;
    private String sourceIp;
    private IntegrationIngestStatus status;
    private String errorMessage;
    private OffsetDateTime occurredAt;
    private OffsetDateTime receivedAt;
    private OffsetDateTime processedAt;
    private String rawPayloadJson;
    private String normalizedPayloadJson;
}
