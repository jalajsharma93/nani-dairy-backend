package net.nani.dairy.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.integration.ConnectorStatus;
import net.nani.dairy.integration.ConnectorType;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationConnectorMetricsResponse {
    private String connectorId;
    private String connectorKey;
    private String name;
    private ConnectorType connectorType;
    private ConnectorStatus status;
    private OffsetDateTime lastSeenAt;
    private String lastError;
    private long totalEvents;
    private long normalizedEvents;
    private long failedEvents;
}
