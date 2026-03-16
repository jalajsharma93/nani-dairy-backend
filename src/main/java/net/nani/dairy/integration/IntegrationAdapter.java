package net.nani.dairy.integration;

import net.nani.dairy.integration.dto.IntegrationIngestRequest;

public interface IntegrationAdapter {
    ConnectorType type();

    NormalizedIntegrationData normalize(IntegrationIngestRequest request);
}
