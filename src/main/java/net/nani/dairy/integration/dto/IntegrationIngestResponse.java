package net.nani.dairy.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.integration.IntegrationIngestStatus;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationIngestResponse {
    private String integrationEventId;
    private String connectorId;
    private String connectorKey;
    private String eventType;
    private String deviceId;
    private IntegrationIngestStatus status;
    private String errorMessage;
    private OffsetDateTime receivedAt;
    private OffsetDateTime processedAt;
}
