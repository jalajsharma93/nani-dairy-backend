package net.nani.dairy.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationMonitoringResponse {
    private OffsetDateTime generatedAt;
    private long totalEvents;
    private long normalizedEvents;
    private long failedEvents;
    private long last24hEvents;
    private long last24hFailedEvents;
    private long activeConnectors;
    private long inactiveConnectors;
    private List<IntegrationConnectorMetricsResponse> connectors;
}
